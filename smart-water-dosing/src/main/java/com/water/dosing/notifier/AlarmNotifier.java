package com.water.dosing.notifier;

import com.water.dosing.entity.AlertRecord;
import com.water.dosing.entity.WaterQuality;
import com.water.dosing.event.AlarmEvent;
import com.water.dosing.event.DataCollectedEvent;
import com.water.dosing.repository.AlertRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AlarmNotifier {

    private static final Logger log = LoggerFactory.getLogger(AlarmNotifier.class);

    private final AlertRecordRepository alertRepo;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${dingtalk.webhook.url:}")
    private String dingtalkWebhook;

    @Value("${dingtalk.enabled:false}")
    private boolean dingtalkEnabled;

    @Value("${dingtalk.timeout.ms:5000}")
    private int timeoutMs;

    @Value("${dingtalk.max-retries:3}")
    private int maxRetries;

    @Value("${dingtalk.retry.delay.ms:1000}")
    private long retryDelayMs;

    @Value("${dingtalk.thread-pool.size:2}")
    private int threadPoolSize;

    @Value("${alarm.outlet-turbidity-threshold:0.5}")
    private double outletTurbidityThreshold;

    @Value("${alarm.outlet-duration-seconds:600}")
    private long outletDurationSeconds;

    @Value("${alarm.flocculation-turbidity-threshold:25.0}")
    private double flocculationTurbidityThreshold;

    private WebClient webClient;
    private ScheduledExecutorService scheduler;
    private final Map<String, Long> alertCooldown = new ConcurrentHashMap<>();
    private final AtomicInteger pendingNotifications = new AtomicInteger(0);

    private Instant firstOutletExceedTime = null;
    private boolean outletAlarmActive = false;

    public AlarmNotifier(AlertRecordRepository alertRepo, ApplicationEventPublisher eventPublisher) {
        this.alertRepo = alertRepo;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs);

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        this.scheduler = new ScheduledThreadPoolExecutor(threadPoolSize, r -> {
            Thread t = new Thread(r, "dingtalk-notifier-" + r.hashCode());
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.CallerRunsPolicy());

        log.info("AlarmNotifier init: dingtalk={}, timeout={}ms, retries={}, outletThreshold={}NTU, outletDuration={}s",
                dingtalkEnabled, timeoutMs, maxRetries, outletTurbidityThreshold, outletDurationSeconds);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            try { scheduler.awaitTermination(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { scheduler.shutdownNow(); Thread.currentThread().interrupt(); }
        }
    }

    @EventListener
    public void onDataCollected(DataCollectedEvent event) {
        evaluateWaterQualityAlarms(event.getWaterQualityList());
    }

    private void evaluateWaterQualityAlarms(List<WaterQuality> wqList) {
        WaterQuality outlet = wqList.stream()
                .filter(w -> "outlet".equals(w.getStage()))
                .findFirst().orElse(null);

        if (outlet != null && outlet.getTurbidity() != null) {
            if (outlet.getTurbidity() > outletTurbidityThreshold) {
                Instant now = Instant.now();
                if (firstOutletExceedTime == null) {
                    firstOutletExceedTime = now;
                } else if (now.isAfter(firstOutletExceedTime.plusSeconds(outletDurationSeconds)) && !outletAlarmActive) {
                    eventPublisher.publishEvent(new AlarmEvent(this, 1, "outlet_turbidity_exceed",
                            String.format("出水浊度 %.2f NTU 超%.1fNTU限值，持续超%d分钟",
                                    outlet.getTurbidity(), outletTurbidityThreshold, outletDurationSeconds / 60)));
                    outletAlarmActive = true;
                }
            } else {
                firstOutletExceedTime = null;
                outletAlarmActive = false;
            }
        }

        WaterQuality flocculation = wqList.stream()
                .filter(w -> "flocculation".equals(w.getStage()))
                .findFirst().orElse(null);

        if (flocculation != null && flocculation.getTurbidity() != null) {
            if (flocculation.getTurbidity() > flocculationTurbidityThreshold) {
                eventPublisher.publishEvent(new AlarmEvent(this, 2, "pump_fault",
                        String.format("絮凝池浊度 %.2f NTU 异常偏高，疑似加药泵故障", flocculation.getTurbidity())));
            }
        }
    }

    @EventListener
    public void onAlarmEvent(AlarmEvent event) {
        AlertRecord alert = new AlertRecord(Instant.now(), (short) event.getLevel(), event.getType(), event.getMessage());
        alert = alertRepo.save(alert);
        log.warn("Alarm [L{}] {}: {}", event.getLevel(), event.getType(), event.getMessage());

        if (dingtalkEnabled && dingtalkWebhook != null && !dingtalkWebhook.isEmpty()) {
            String key = event.getLevel() + ":" + event.getType();
            long now = System.currentTimeMillis();
            Long last = alertCooldown.get(key);
            long cooldownMs = event.getLevel() == 1 ? 60000 : 300000;
            if (last == null || now - last > cooldownMs) {
                alertCooldown.put(key, now);
                sendDingTalkAsync(alert);
            }
        }
    }

    private void sendDingTalkAsync(AlertRecord alert) {
        pendingNotifications.incrementAndGet();
        scheduler.submit(() -> {
            try { sendWithRetry(alert); }
            finally { pendingNotifications.decrementAndGet(); }
        });
    }

    private void sendWithRetry(AlertRecord alert) {
        String levelText = alert.getLevel() == 1 ? "一级告警" : "二级告警";
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("title", levelText + " - " + alert.getType());
        markdown.put("text", "### " + levelText + "\n\n- **等级**: " + levelText
                + "\n\n- **类型**: " + alert.getType()
                + "\n\n- **时间**: " + alert.getTime().toString().replace("T", " ")
                + "\n\n- **详情**: " + alert.getMessage() + "\n\n");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", markdown);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String resp = webClient.post().uri(dingtalkWebhook).bodyValue(body)
                        .retrieve().bodyToMono(String.class)
                        .timeout(Duration.ofMillis(timeoutMs)).block();
                if (resp != null && resp.contains("\"errcode\":0")) {
                    log.info("DingTalk sent on attempt {}", attempt);
                    return;
                }
                log.warn("DingTalk bad response attempt {}: {}", attempt, resp);
            } catch (Exception e) {
                log.warn("DingTalk failed attempt {}: {}", attempt, e.getMessage());
            }
            if (attempt < maxRetries) {
                try { Thread.sleep(retryDelayMs * (1L << (attempt - 1))); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }
        }
        log.error("DingTalk failed after {} attempts", maxRetries);
    }

    public List<AlertRecord> getUnacknowledgedAlerts() { return alertRepo.findUnacknowledged(); }
    public List<AlertRecord> getRecentAlerts() { return alertRepo.findTop50ByOrderByTimeDesc(); }

    public AlertRecord acknowledge(Integer id) {
        AlertRecord a = alertRepo.findById(id).orElse(null);
        if (a != null) { a.setAcknowledged(true); alertRepo.save(a); }
        return a;
    }

    public Map<String, Object> getNotificationStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("enabled", dingtalkEnabled);
        s.put("pending", pendingNotifications.get());
        s.put("timeoutMs", timeoutMs);
        s.put("maxRetries", maxRetries);
        return s;
    }
}
