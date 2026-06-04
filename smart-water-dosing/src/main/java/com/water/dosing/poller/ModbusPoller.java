package com.water.dosing.poller;

import com.water.dosing.entity.WaterQuality;
import com.water.dosing.event.DataCollectedEvent;
import com.water.dosing.service.WaterQualityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class ModbusPoller {

    private static final Logger log = LoggerFactory.getLogger(ModbusPoller.class);

    private final TaskScheduler taskScheduler;
    private final WaterQualityService wqService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${modbus.host:127.0.0.1}")
    private String modbusHost;

    @Value("${modbus.port:502}")
    private int modbusPort;

    @Value("${modbus.poll.interval:300000}")
    private long pollIntervalMs;

    @Value("${modbus.connect.timeout:3000}")
    private int connectTimeoutMs;

    @Value("${modbus.read.timeout:5000}")
    private int readTimeoutMs;

    private ScheduledFuture<?> scheduledTask;
    private final ReentrantLock collectionLock = new ReentrantLock();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final List<StageTask> stageTasks = Arrays.asList(
            new StageTask("raw_water", 0, 10),
            new StageTask("flocculation", 20, 8),
            new StageTask("sedimentation", 40, 6),
            new StageTask("filtration", 60, 4),
            new StageTask("outlet", 80, 9)
    );

    public ModbusPoller(TaskScheduler taskScheduler, WaterQualityService wqService,
                        ApplicationEventPublisher eventPublisher) {
        this.taskScheduler = taskScheduler;
        this.wqService = wqService;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void start() {
        log.info("ModbusPoller starting: host={}:{}, interval={}ms, stages={}",
                modbusHost, modbusPort, pollIntervalMs, stageTasks.size());
        scheduledTask = taskScheduler.scheduleAtFixedRate(this::pollCycle, pollIntervalMs);
    }

    @PreDestroy
    public void stop() {
        if (scheduledTask != null) scheduledTask.cancel(false);
        isRunning.set(false);
    }

    private void pollCycle() {
        if (!collectionLock.tryLock()) {
            log.warn("Previous poll still in progress, skipping");
            return;
        }
        try {
            isRunning.set(true);
            long t0 = System.currentTimeMillis();

            List<StageTask> prioritized = new ArrayList<>(stageTasks);
            prioritized.sort((a, b) -> Integer.compare(b.priority, a.priority));

            Map<String, float[]> results = new LinkedHashMap<>();
            Socket socket = null;
            DataOutputStream out = null;
            DataInputStream in = null;
            boolean connected = false;
            short txId = 1;

            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(modbusHost, modbusPort), connectTimeoutMs);
                socket.setSoTimeout(readTimeoutMs);
                out = new DataOutputStream(socket.getOutputStream());
                in = new DataInputStream(socket.getInputStream());
                connected = true;
            } catch (Exception e) {
                log.warn("Modbus TCP connection failed: {}", e.getMessage());
            }

            for (StageTask task : prioritized) {
                if (!isRunning.get()) break;
                try {
                    float[] values = connected
                            ? readRegisters(out, in, task.baseAddr, txId++)
                            : simulateData(task.stage);
                    results.put(task.stage, values);
                } catch (Exception e) {
                    log.error("Stage {} read failed, falling back to simulation", task.stage);
                    try { results.put(task.stage, simulateData(task.stage)); } catch (Exception ignored) {}
                }
            }

            if (socket != null) try { socket.close(); } catch (Exception ignored) {}

            List<WaterQuality> wqList = persistWaterQuality(results);

            if (!wqList.isEmpty()) {
                eventPublisher.publishEvent(new DataCollectedEvent(this, wqList, results));
            }

            log.info("Poll cycle completed in {}ms, {} stages", System.currentTimeMillis() - t0, results.size());
        } catch (Exception e) {
            log.error("Poll cycle failed", e);
        } finally {
            isRunning.set(false);
            collectionLock.unlock();
        }
    }

    private List<WaterQuality> persistWaterQuality(Map<String, float[]> results) {
        List<WaterQuality> list = new ArrayList<>();
        String[] order = {"raw_water", "flocculation", "sedimentation", "filtration", "outlet"};
        for (String stage : order) {
            float[] r = results.get(stage);
            if (r == null) continue;
            WaterQuality wq = new WaterQuality();
            wq.setTime(Instant.now());
            wq.setStage(stage);
            wq.setTurbidity((double) r[0]);
            wq.setPh((double) r[1]);
            wq.setTemperature((double) r[2]);
            wq.setAmmonia((double) r[3]);
            wq.setCod((double) r[4]);
            wq.setFlowRate((double) r[5]);
            list.add(wq);
        }
        if (!list.isEmpty()) wqService.saveAll(list);
        return list;
    }

    private float[] readRegisters(DataOutputStream out, DataInputStream in, int baseAddr, short txId) throws Exception {
        byte[] req = buildReadRequest(txId, 1, baseAddr, 16);
        out.write(req);
        out.flush();
        byte[] hdr = new byte[9];
        in.readFully(hdr);
        int byteCount = hdr[8] & 0xFF;
        byte[] data = new byte[byteCount];
        in.readFully(data);
        float[] values = new float[8];
        for (int i = 0; i < 8; i++) {
            int raw = ((data[i * 4] & 0xFF) << 24) | ((data[i * 4 + 1] & 0xFF) << 16)
                    | ((data[i * 4 + 2] & 0xFF) << 8) | (data[i * 4 + 3] & 0xFF);
            values[i] = Float.intBitsToFloat(raw);
        }
        return values;
    }

    private byte[] buildReadRequest(short txId, int unitId, int startAddr, int quantity) {
        byte[] f = new byte[12];
        f[0] = (byte) ((txId >> 8) & 0xFF); f[1] = (byte) (txId & 0xFF);
        f[2] = 0; f[3] = 0; f[4] = 0; f[5] = 6;
        f[6] = (byte) unitId; f[7] = 3;
        f[8] = (byte) ((startAddr >> 8) & 0xFF); f[9] = (byte) (startAddr & 0xFF);
        f[10] = (byte) ((quantity >> 8) & 0xFF); f[11] = (byte) (quantity & 0xFF);
        return f;
    }

    private final ThreadLocal<Random> rng = ThreadLocal.withInitial(Random::new);
    private volatile double smTurb = 15, smPh = 7.1, smTemp = 18, smFlow = 8333;

    private float[] simulateData(String stage) {
        Random r = rng.get();
        double h = Instant.now().getEpochSecond() % 86400 / 3600.0;
        double df = Math.sin(h / 24 * 2 * Math.PI);
        double turb = Math.max(2, 15 + 5 * df + r.nextGaussian() * 2);
        double ph = Math.max(6, Math.min(9, 7.1 + r.nextGaussian() * 0.1));
        double temp = Math.max(5, 18 + 3 * df + r.nextGaussian() * 0.5);
        double nh3 = Math.max(0.01, 0.12 + r.nextGaussian() * 0.02);
        double cod = Math.max(0.5, 2.5 + r.nextGaussian() * 0.3);
        double flow = Math.max(6000, 8333 + r.nextGaussian() * 200);
        double coag = 2.5 + turb * 0.15 + flow * 0.001 + r.nextGaussian() * 0.3;
        double chl = 1.5 + r.nextGaussian() * 0.2;
        smTurb = smTurb * 0.95 + turb * 0.05;
        smPh = smPh * 0.95 + ph * 0.05;
        smTemp = smTemp * 0.95 + temp * 0.05;
        smFlow = smFlow * 0.95 + flow * 0.05;
        switch (stage) {
            case "raw_water": return f(turb, ph, temp, nh3, cod, flow, coag, chl);
            case "flocculation": return f(turb*0.5+r.nextGaussian(), ph-0.1+r.nextGaussian()*0.05, temp-0.1, nh3*0.6, cod*0.5, flow, 0, 0);
            case "sedimentation": return f(turb*0.12+r.nextGaussian()*0.3, ph+r.nextGaussian()*0.05, temp-0.2, nh3*0.2, cod*0.25, flow, 0, 0);
            case "filtration": return f(turb*0.02+r.nextGaussian()*0.05, ph+r.nextGaussian()*0.03, temp-0.3, nh3*0.08, cod*0.1, flow, 0, 0);
            case "outlet": return f(turb*0.015+r.nextGaussian()*0.02, ph+r.nextGaussian()*0.03, temp-0.3, nh3*0.05, cod*0.07, flow, 0, 0);
            default: return new float[8];
        }
    }

    private float[] f(double a, double b, double c, double d, double e, double f, double g, double h) {
        return new float[]{(float)a,(float)b,(float)c,(float)d,(float)e,(float)f,(float)g,(float)h};
    }

    private static class StageTask {
        final String stage; final int baseAddr; final int priority;
        StageTask(String s, int a, int p) { stage=s; baseAddr=a; priority=p; }
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("isRunning", isRunning.get());
        s.put("isLocked", collectionLock.isLocked());
        s.put("host", modbusHost);
        s.put("port", modbusPort);
        return s;
    }
}
