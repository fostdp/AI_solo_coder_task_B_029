package com.water.dosing.module.membrane;

import com.water.dosing.entity.MembraneCleanRecord;
import com.water.dosing.entity.MembraneModule;
import com.water.dosing.module.membrane.dto.FoulingEvaluationResult;
import com.water.dosing.module.membrane.stream.MembraneStreamProcessor;
import com.water.dosing.repository.MembraneCleanRecordRepository;
import com.water.dosing.repository.MembraneModuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class MembraneMonitor {

    private static final Logger log = LoggerFactory.getLogger(MembraneMonitor.class);
    private static final String MODULE_NAME = "MembraneMonitor";
    private static final String MODULE_VERSION = "1.0.0";

    private final MembraneModuleRepository moduleRepo;
    private final MembraneCleanRecordRepository cleanRecordRepo;
    private final MembraneMonitorConfig config;
    private final MembraneStreamProcessor streamProcessor;

    public MembraneMonitor(MembraneModuleRepository moduleRepo,
                            MembraneCleanRecordRepository cleanRecordRepo,
                            MembraneMonitorConfig config,
                            MembraneStreamProcessor streamProcessor) {
        this.moduleRepo = moduleRepo;
        this.cleanRecordRepo = cleanRecordRepo;
        this.config = config;
        this.streamProcessor = streamProcessor;
    }

    @PostConstruct
    public void init() {
        log.info("[{}] Initializing module version {}", MODULE_NAME, MODULE_VERSION);
        List<MembraneModule> active = moduleRepo.findAllActive();
        if (active.isEmpty()) {
            initializeDefaultMembranes();
        }

        if (config.isStreamProcessingEnabled()) {
            log.info("[{}] Stream processing enabled, starting Flink pipeline", MODULE_NAME);
            List<MembraneModule> latestModules = new ArrayList<>();
            for (MembraneModule m : moduleRepo.findAllActive()) {
                List<MembraneModule> latest = moduleRepo.findLatestByModuleCode(m.getModuleCode());
                if (!latest.isEmpty()) {
                    latestModules.add(latest.get(0));
                }
            }
            streamProcessor.startStreamProcessing(latestModules, config.getStreamParallelism());
        }

        log.info("[{}] Module initialized successfully, streamProcessing={}",
                MODULE_NAME, config.isStreamProcessingEnabled());
    }

    private void initializeDefaultMembranes() {
        log.info("[{}] Initializing default membrane modules", MODULE_NAME);
        Instant now = Instant.now();
        Instant lastClean = now.minus(15, ChronoUnit.DAYS);

        MembraneModule uf1 = new MembraneModule(now, "UF-01", "超滤膜组1号", "ultrafiltration");
        uf1.setStage("ultrafiltration");
        uf1.setFlux(52.5);
        uf1.setTmd(1.05);
        uf1.setPressureIn(2.1);
        uf1.setPressureOut(1.05);
        uf1.setPermeateFlow(120.0);
        uf1.setRejectFlow(15.0);
        uf1.setRecoveryRate(88.9);
        uf1.setFoulingIndex(0.82);
        uf1.setFoulingLevel(2);
        uf1.setLastCleanTime(lastClean);
        uf1.setPredictedCleanDays(5);
        uf1.setCleanUrgency("soon");
        uf1.setCumulativeVolume(45000.0);
        uf1.setOperatingHours(360.0);
        moduleRepo.save(uf1);

        MembraneModule uf2 = new MembraneModule(now, "UF-02", "超滤膜组2号", "ultrafiltration");
        uf2.setStage("ultrafiltration");
        uf2.setFlux(58.2);
        uf2.setTmd(0.88);
        uf2.setPressureIn(2.0);
        uf2.setPressureOut(1.12);
        uf2.setPermeateFlow(135.0);
        uf2.setRejectFlow(16.5);
        uf2.setRecoveryRate(89.1);
        uf2.setFoulingIndex(0.93);
        uf2.setFoulingLevel(1);
        uf2.setLastCleanTime(lastClean);
        uf2.setPredictedCleanDays(12);
        uf2.setCleanUrgency("normal");
        uf2.setCumulativeVolume(38000.0);
        uf2.setOperatingHours(340.0);
        moduleRepo.save(uf2);

        MembraneModule ro1 = new MembraneModule(now, "RO-01", "反渗透膜组1号", "reverse_osmosis");
        ro1.setStage("reverse_osmosis");
        ro1.setFlux(21.8);
        ro1.setTmd(12.5);
        ro1.setPressureIn(15.0);
        ro1.setPressureOut(2.5);
        ro1.setPermeateFlow(85.0);
        ro1.setRejectFlow(65.0);
        ro1.setRecoveryRate(56.7);
        ro1.setFoulingIndex(0.78);
        ro1.setFoulingLevel(2);
        ro1.setLastCleanTime(lastClean);
        ro1.setPredictedCleanDays(4);
        ro1.setCleanUrgency("soon");
        ro1.setCumulativeVolume(28000.0);
        ro1.setOperatingHours(350.0);
        moduleRepo.save(ro1);

        MembraneModule ro2 = new MembraneModule(now, "RO-02", "反渗透膜组2号", "reverse_osmosis");
        ro2.setStage("reverse_osmosis");
        ro2.setFlux(18.5);
        ro2.setTmd(15.8);
        ro2.setPressureIn(16.5);
        ro2.setPressureOut(0.7);
        ro2.setPermeateFlow(72.0);
        ro2.setRejectFlow(78.0);
        ro2.setRecoveryRate(48.0);
        ro2.setFoulingIndex(0.48);
        ro2.setFoulingLevel(3);
        ro2.setLastCleanTime(lastClean);
        ro2.setPredictedCleanDays(1);
        ro2.setCleanUrgency("urgent");
        ro2.setCumulativeVolume(52000.0);
        ro2.setOperatingHours(420.0);
        moduleRepo.save(ro2);
    }

    public Map<String, Object> getModuleInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("moduleName", MODULE_NAME);
        info.put("version", MODULE_VERSION);
        info.put("status", "active");
        info.put("streamProcessingEnabled", config.isStreamProcessingEnabled());
        info.put("streamRunning", streamProcessor.isRunning());
        info.put("baselineWarmupHours", config.getBaselineWarmupHours());
        info.put("replacementDetectionThreshold", config.getReplacementDetectionThreshold());
        info.put("activeModules", moduleRepo.findAllActive().size());
        info.put("streamStats", streamProcessor.getStreamStats());
        return info;
    }

    public Map<String, Object> getAllModulesStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", MODULE_NAME);
        result.put("moduleVersion", MODULE_VERSION);

        List<MembraneModule> allModules = moduleRepo.findAllActive();

        Map<String, List<MembraneModule>> byType = allModules.stream()
                .collect(Collectors.groupingBy(MembraneModule::getMembraneType));

        List<Map<String, Object>> modules = new ArrayList<>();
        for (MembraneModule m : allModules) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("moduleCode", m.getModuleCode());
            item.put("moduleName", m.getModuleName());
            item.put("membraneType", m.getMembraneType());
            item.put("flux", m.getFlux());
            item.put("tmd", m.getTmd());
            item.put("baselineFlux", m.getBaselineFlux());
            item.put("baselineTmd", m.getBaselineTmd());
            item.put("foulingIndex", m.getFoulingIndex());
            item.put("foulingLevel", m.getFoulingLevel());
            item.put("predictedCleanDays", m.getPredictedCleanDays());
            item.put("cleanUrgency", m.getCleanUrgency());
            item.put("recoveryRate", m.getRecoveryRate());
            item.put("isReplacedRecently", m.getIsReplacedRecently());
            item.put("membraneAgeHours", m.getMembraneAgeHours());
            item.put("lastCleanTime", m.getLastCleanTime() != null ? m.getLastCleanTime().toString() : null);
            item.put("membraneReplaceTime", m.getMembraneReplaceTime() != null ? m.getMembraneReplaceTime().toString() : null);

            MembraneStreamProcessor.MembraneStreamResult streamResult =
                    streamProcessor.getLatestResult(m.getModuleCode());
            if (streamResult != null) {
                item.put("streamAvgFlux", streamResult.getAvgFlux());
                item.put("streamFoulingTrend", streamResult.getFoulingTrend());
                item.put("streamAlertTriggered", streamResult.isAlertTriggered());
                item.put("potentialReplacement", streamResult.isPotentialReplacement());
            }

            modules.add(item);
        }
        result.put("modules", modules);

        List<String> urgentCodes = new ArrayList<>();
        List<String> soonCodes = new ArrayList<>();
        List<String> replacementCodes = new ArrayList<>();
        for (MembraneModule m : allModules) {
            if ("urgent".equals(m.getCleanUrgency())) {
                urgentCodes.add(m.getModuleCode());
            } else if ("soon".equals(m.getCleanUrgency())) {
                soonCodes.add(m.getModuleCode());
            }
            if (Boolean.TRUE.equals(m.getIsReplacedRecently())) {
                replacementCodes.add(m.getModuleCode());
            }
        }
        result.put("urgentModules", urgentCodes);
        result.put("soonModules", soonCodes);
        result.put("recentlyReplacedModules", replacementCodes);
        result.put("needAttentionCount", urgentCodes.size() + soonCodes.size());

        return result;
    }

    public MembraneModule getModuleLatest(String moduleCode) {
        List<MembraneModule> latest = moduleRepo.findLatestByModuleCode(moduleCode);
        return latest.isEmpty() ? null : latest.get(0);
    }

    public List<MembraneModule> getModuleHistory(String moduleCode, int hours) {
        Instant start = Instant.now().minus(hours, ChronoUnit.HOURS);
        return moduleRepo.findByModuleCodeOrderByTimeDesc(moduleCode).stream()
                .filter(m -> m.getTime().isAfter(start))
                .limit(200)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getFoulingTrend(String moduleCode, int hours) {
        List<MembraneModule> history = getModuleHistory(moduleCode, hours);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (MembraneModule m : history) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", m.getTime().toString());
            item.put("flux", m.getFlux());
            item.put("tmd", m.getTmd());
            item.put("foulingIndex", m.getFoulingIndex());
            item.put("baselineFlux", m.getBaselineFlux());
            item.put("baselineTmd", m.getBaselineTmd());
            trend.add(item);
        }
        return trend;
    }

    @Transactional
    public FoulingEvaluationResult evaluateFoulingAndPredictCleaning() {
        return evaluateFoulingAndPredictCleaning(false);
    }

    @Transactional
    public FoulingEvaluationResult evaluateFoulingAndPredictCleaning(boolean useStreamProcessing) {
        log.info("[{}] Starting fouling evaluation, streamProcessing={}",
                MODULE_NAME, useStreamProcessing);

        FoulingEvaluationResult result = new FoulingEvaluationResult();
        result.setEvaluationId("EVAL-" + System.currentTimeMillis());
        result.setEvaluationTime(Instant.now());
        result.setProcessingMode(useStreamProcessing ? "STREAM" : "BATCH");
        result.setUsedStreamProcessing(useStreamProcessing);

        List<String> moduleCodes = moduleRepo.findAllActiveModuleCodes();
        result.setTotalModules(moduleCodes.size());

        List<FoulingEvaluationResult.ModuleFoulingStatus> statuses = new ArrayList<>();
        int urgentCount = 0;
        int soonCount = 0;
        int normalCount = 0;

        for (String moduleCode : moduleCodes) {
            FoulingEvaluationResult.ModuleFoulingStatus status =
                    evaluateModule(moduleCode, useStreamProcessing);
            statuses.add(status);

            String urgency = status.getCleanUrgency();
            if ("urgent".equals(urgency)) urgentCount++;
            else if ("soon".equals(urgency)) soonCount++;
            else normalCount++;

            MembraneModule latest = getModuleLatest(moduleCode);
            if (latest != null && useStreamProcessing) {
                streamProcessor.processModuleData(latest);
            }
        }

        result.setModuleStatuses(statuses);
        result.setUrgentCount(urgentCount);
        result.setSoonCount(soonCount);
        result.setNormalCount(normalCount);

        if (useStreamProcessing) {
            result.setStreamStats(streamProcessor.getStreamStats());
        }

        log.info("[{}] Fouling evaluation complete: urgent={}, soon={}, mode={}",
                MODULE_NAME, urgentCount, soonCount, result.getProcessingMode());

        return result;
    }

    @Async("moduleTaskExecutor")
    public CompletableFuture<FoulingEvaluationResult> evaluateFoulingAsync(boolean useStreamProcessing) {
        log.info("[{}] Starting async fouling evaluation", MODULE_NAME);
        return CompletableFuture.completedFuture(evaluateFoulingAndPredictCleaning(useStreamProcessing));
    }

    private FoulingEvaluationResult.ModuleFoulingStatus evaluateModule(
            String moduleCode, boolean useStreamProcessing) {

        FoulingEvaluationResult.ModuleFoulingStatus status = new FoulingEvaluationResult.ModuleFoulingStatus();
        status.setModuleCode(moduleCode);

        List<MembraneModule> history = getModuleHistory(moduleCode, 72);
        if (history.isEmpty()) {
            status.setNotes("No data available");
            return status;
        }

        MembraneModule latest = history.get(0);
        status.setModuleName(latest.getModuleName());
        status.setModuleType(latest.getMembraneType());
        status.setCurrentFlux(latest.getFlux() != null ? latest.getFlux() : 0);
        status.setCurrentTmd(latest.getTmd() != null ? latest.getTmd() : 0);

        boolean replaced = detectMembraneReplacement(moduleCode, history);
        status.setRecentlyReplaced(Boolean.TRUE.equals(latest.getIsReplacedRecently()));
        status.setReplacementTime(latest.getMembraneReplaceTime());

        if (replaced) {
            status.setNotes("Membrane replacement detected, baseline reset");
        }

        double baseFlux = getEffectiveBaselineFlux(latest);
        double baseTmd = getEffectiveBaselineTmd(latest);
        status.setBaselineFlux(baseFlux);
        status.setBaselineTmd(baseTmd);

        double normalizedFlux = latest.getFlux() != null ? latest.getFlux() / baseFlux : 1.0;
        double normalizedTmd = latest.getTmd() != null ? baseTmd / latest.getTmd() : 1.0;
        double foulingIndex = (normalizedFlux + normalizedTmd) / 2;
        status.setFoulingIndex(Math.round(foulingIndex * 100.0) / 100.0);

        if (history.size() >= 5) {
            double previousFlux = history.get(Math.min(4, history.size() - 1)).getFlux();
            if (previousFlux != null && previousFlux > 0 && latest.getFlux() != null) {
                latest.setFluxChangeRate((latest.getFlux() - previousFlux) / previousFlux);
            }
        }

        if (latest.getBaselineFlux() == null) {
            initializeModuleBaseline(latest, baseFlux, baseTmd);
        }

        int foulingLevel;
        String cleanUrgency;
        int predictedDays;

        if (Boolean.TRUE.equals(latest.getIsReplacedRecently())) {
            foulingLevel = 1;
            cleanUrgency = "normal";
            predictedDays = 30;
        } else if (foulingIndex >= config.getFoulingWarningThreshold()) {
            foulingLevel = 1;
            cleanUrgency = "normal";
            predictedDays = predictCleanDays(history, foulingIndex, baseFlux);
        } else if (foulingIndex >= config.getFoulingAlarmThreshold()) {
            foulingLevel = 2;
            cleanUrgency = "soon";
            predictedDays = Math.max(1, (int) ((foulingIndex - config.getFoulingAlarmThreshold()) /
                    (config.getFoulingWarningThreshold() - config.getFoulingAlarmThreshold()) *
                    config.getSoonDays()));
        } else {
            foulingLevel = 3;
            cleanUrgency = "urgent";
            predictedDays = 1;
        }

        String levelStr = foulingLevel == 1 ? "good" : foulingLevel == 2 ? "moderate" : "severe";
        status.setFoulingLevel(levelStr);
        status.setCleanUrgency(cleanUrgency);
        status.setPredictedCleanDays(predictedDays);

        latest.setFoulingIndex(foulingIndex);
        latest.setFoulingLevel(foulingLevel);
        latest.setPredictedCleanDays(predictedDays);
        latest.setCleanUrgency(cleanUrgency);
        moduleRepo.save(latest);

        return status;
    }

    private boolean detectMembraneReplacement(String moduleCode, List<MembraneModule> history) {
        if (history.size() < 5) return false;

        MembraneModule latest = history.get(0);

        if (Boolean.TRUE.equals(latest.getIsReplacedRecently())) {
            Instant warmupEnd = latest.getMembraneReplaceTime() != null ?
                    latest.getMembraneReplaceTime().plus(config.getBaselineWarmupHours(), ChronoUnit.HOURS) :
                    Instant.now();
            if (Instant.now().isAfter(warmupEnd)) {
                latest.setIsReplacedRecently(false);
                log.info("[{}] Membrane {} baseline warmup period ended, normal evaluation resumed",
                        MODULE_NAME, moduleCode);
            }
            return false;
        }

        double avgFluxBefore = 0;
        double avgTmdBefore = 0;
        int count = 0;
        for (int i = Math.min(5, history.size() - 1); i >= 2 && count < 3; i--) {
            MembraneModule m = history.get(i);
            if (m.getFlux() != null && m.getTmd() != null) {
                avgFluxBefore += m.getFlux();
                avgTmdBefore += m.getTmd();
                count++;
            }
        }

        if (count < 2 || latest.getFlux() == null || latest.getTmd() == null) return false;

        avgFluxBefore /= count;
        avgTmdBefore /= count;

        double fluxIncrease = (latest.getFlux() - avgFluxBefore) / avgFluxBefore;
        double tmdDecrease = (avgTmdBefore - latest.getTmd()) / avgTmdBefore;

        if (fluxIncrease > config.getReplacementDetectionThreshold() &&
                tmdDecrease > config.getReplacementDetectionThreshold() * 0.5) {
            log.info("[{}] Membrane replacement detected for {}: fluxIncrease={}%, tmdDecrease={}%",
                    MODULE_NAME, moduleCode,
                    String.format("%.1f", fluxIncrease * 100),
                    String.format("%.1f", tmdDecrease * 100));

            resetModuleBaseline(latest, fluxIncrease, tmdDecrease);
            return true;
        }

        return false;
    }

    private void resetModuleBaseline(MembraneModule module, double fluxIncrease, double tmdDecrease) {
        boolean isRO = "reverse_osmosis".equals(module.getMembraneType());
        double defaultBaseFlux = isRO ? config.getRoBaseFlux() : config.getUfBaseFlux();
        double defaultBaseTmd = isRO ? config.getRoBaseTmd() : config.getUfBaseTmd();

        module.setBaselineFlux(Math.max(module.getFlux() * 0.98, defaultBaseFlux));
        module.setBaselineTmd(Math.min(module.getTmd() * 1.02, defaultBaseTmd));
        module.setIsReplacedRecently(true);
        module.setMembraneReplaceTime(Instant.now());
        module.setMembraneAgeHours(0.0);
        module.setFoulingIndex(0.98);
        module.setFoulingLevel(1);
        module.setPredictedCleanDays(30);
        module.setCleanUrgency("normal");

        if (module.getCumulativeVolume() != null) {
            module.setCumulativeVolume(0.0);
        }
    }

    private void initializeModuleBaseline(MembraneModule module, double defaultFlux, double defaultTmd) {
        boolean isRO = "reverse_osmosis".equals(module.getMembraneType());
        double baseFlux = isRO ? config.getRoBaseFlux() : config.getUfBaseFlux();
        double baseTmd = isRO ? config.getRoBaseTmd() : config.getUfBaseTmd();

        module.setBaselineFlux(baseFlux);
        module.setBaselineTmd(baseTmd);

        if (module.getMembraneReplaceTime() == null) {
            module.setMembraneReplaceTime(module.getTime());
        }
        if (module.getMembraneAgeHours() == null && module.getOperatingHours() != null) {
            module.setMembraneAgeHours(module.getOperatingHours());
        }
    }

    private double getEffectiveBaselineFlux(MembraneModule module) {
        if (module.getBaselineFlux() != null && module.getBaselineFlux() > 0) {
            return module.getBaselineFlux();
        }
        boolean isRO = "reverse_osmosis".equals(module.getMembraneType());
        return isRO ? config.getRoBaseFlux() : config.getUfBaseFlux();
    }

    private double getEffectiveBaselineTmd(MembraneModule module) {
        if (module.getBaselineTmd() != null && module.getBaselineTmd() > 0) {
            return module.getBaselineTmd();
        }
        boolean isRO = "reverse_osmosis".equals(module.getMembraneType());
        return isRO ? config.getRoBaseTmd() : config.getUfBaseTmd();
    }

    private int predictCleanDays(List<MembraneModule> history, double currentFouling, double baseFlux) {
        if (history.size() < 5) return 14;

        double[] foulingTrend = new double[Math.min(history.size(), 20)];
        long[] timeHours = new long[foulingTrend.length];

        Instant now = Instant.now();
        for (int i = 0; i < foulingTrend.length; i++) {
            MembraneModule m = history.get(history.size() - 1 - i);
            timeHours[i] = ChronoUnit.HOURS.between(m.getTime(), now);
            double nf = m.getFlux() != null ? m.getFlux() / baseFlux : 1.0;
            foulingTrend[i] = nf;
        }

        double slope = calculateSlope(timeHours, foulingTrend);

        if (slope >= 0) return 30;

        double threshold = config.getFoulingAlarmThreshold();
        double hoursToThreshold = (threshold - currentFouling) / slope;

        return Math.max(1, Math.min(30, (int) Math.ceil(hoursToThreshold / 24)));
    }

    private double calculateSlope(long[] x, double[] y) {
        if (x.length < 2) return 0;

        double xMean = 0, yMean = 0;
        for (long v : x) xMean += v;
        for (double v : y) yMean += v;
        xMean /= x.length;
        yMean /= y.length;

        double numerator = 0, denominator = 0;
        for (int i = 0; i < x.length; i++) {
            double xd = x[i] - xMean;
            double yd = y[i] - yMean;
            numerator += xd * yd;
            denominator += xd * xd;
        }

        return denominator > 0 ? numerator / denominator : 0;
    }

    @Transactional
    public MembraneCleanRecord recordCleaning(MembraneCleanRecord record) {
        double baseFlux = config.getUfBaseFlux();
        if (record.getFluxBefore() != null && record.getFluxAfter() != null && record.getFluxBefore() > 0) {
            double recovery = ((record.getFluxAfter() - record.getFluxBefore()) /
                    (baseFlux - record.getFluxBefore())) * 100;
            record.setFluxRecoveryPct(Math.min(100, Math.max(0, recovery)));
        }

        MembraneCleanRecord saved = cleanRecordRepo.save(record);

        List<MembraneModule> modules = moduleRepo.findLatestByModuleCode(record.getModuleCode());
        if (!modules.isEmpty()) {
            MembraneModule module = modules.get(0);
            module.setLastCleanTime(record.getCleanTime());
            module.setFoulingIndex(0.95);
            module.setFoulingLevel(1);
            module.setPredictedCleanDays(30);
            module.setCleanUrgency("normal");
            moduleRepo.save(module);

            if (streamProcessor.isRunning()) {
                streamProcessor.processModuleData(module);
            }
        }

        log.info("[{}] Cleaning recorded for {}: type={}, recovery={}%",
                MODULE_NAME, record.getModuleCode(), record.getCleanType(), record.getFluxRecoveryPct());
        return saved;
    }

    public List<MembraneCleanRecord> getCleanHistory(String moduleCode, int days) {
        Instant start = Instant.now().minus(days, ChronoUnit.DAYS);
        return cleanRecordRepo.findByCleanTimeBetweenOrderByCleanTimeDesc(start, Instant.now()).stream()
                .filter(r -> moduleCode == null || moduleCode.equals(r.getModuleCode()))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getCleaningStats(int days) {
        Instant start = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> stats = cleanRecordRepo.getCleaningStats(start);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", MODULE_NAME);
        List<Map<String, Object>> moduleStats = new ArrayList<>();
        int totalCleanings = 0;
        double totalRecovery = 0;

        for (Object[] row : stats) {
            String code = (String) row[0];
            Long count = (Long) row[1];
            Double avgRecovery = (Double) row[2];

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("moduleCode", code);
            item.put("cleanCount", count);
            item.put("avgRecovery", avgRecovery != null ? Math.round(avgRecovery * 100.0) / 100.0 : null);
            moduleStats.add(item);

            totalCleanings += count != null ? count : 0;
            totalRecovery += avgRecovery != null ? avgRecovery : 0;
        }

        result.put("totalCleanings", totalCleanings);
        result.put("avgRecovery", moduleStats.size() > 0 ?
                Math.round(totalRecovery / moduleStats.size() * 100.0) / 100.0 : null);
        result.put("moduleStats", moduleStats);

        return result;
    }

    public List<Map<String, Object>> getCleaningSchedule() {
        List<MembraneModule> urgent = moduleRepo.findModulesNeedingCleaning(
                Arrays.asList("urgent", "soon"));

        List<Map<String, Object>> schedule = new ArrayList<>();
        for (MembraneModule m : urgent) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("moduleCode", m.getModuleCode());
            item.put("moduleName", m.getModuleName());
            item.put("membraneType", m.getMembraneType());
            item.put("cleanUrgency", m.getCleanUrgency());
            item.put("predictedCleanDays", m.getPredictedCleanDays());
            item.put("foulingIndex", m.getFoulingIndex());
            item.put("foulingLevel", m.getFoulingLevel());
            item.put("lastCleanTime", m.getLastCleanTime() != null ? m.getLastCleanTime().toString() : null);
            item.put("isReplacedRecently", m.getIsReplacedRecently());
            schedule.add(item);
        }

        schedule.sort((a, b) -> {
            String ua = (String) a.get("cleanUrgency");
            String ub = (String) b.get("cleanUrgency");
            if (!ua.equals(ub)) return ua.equals("urgent") ? -1 : 1;
            Integer da = (Integer) a.get("predictedCleanDays");
            Integer db = (Integer) b.get("predictedCleanDays");
            return da.compareTo(db);
        });

        return schedule;
    }

    @Transactional
    public Map<String, Object> recordMembraneReplacement(String moduleCode, String serialNo, String operator) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<MembraneModule> latest = moduleRepo.findLatestByModuleCode(moduleCode);
        if (latest.isEmpty()) {
            result.put("success", false);
            result.put("message", "Module not found: " + moduleCode);
            return result;
        }

        MembraneModule module = latest.get(0);
        boolean isRO = "reverse_osmosis".equals(module.getMembraneType());

        double baseFlux = isRO ? config.getRoBaseFlux() : config.getUfBaseFlux();
        double baseTmd = isRO ? config.getRoBaseTmd() : config.getUfBaseTmd();

        if (module.getFlux() != null && module.getFlux() > 0) {
            baseFlux = module.getFlux() * 0.98;
        }
        if (module.getTmd() != null && module.getTmd() > 0) {
            baseTmd = module.getTmd() * 1.02;
        }

        module.setBaselineFlux(baseFlux);
        module.setBaselineTmd(baseTmd);
        module.setMembraneSerialNo(serialNo);
        module.setMembraneReplaceTime(Instant.now());
        module.setMembraneAgeHours(0.0);
        module.setIsReplacedRecently(true);
        module.setFoulingIndex(0.98);
        module.setFoulingLevel(1);
        module.setPredictedCleanDays(30);
        module.setCleanUrgency("normal");
        module.setCumulativeVolume(0.0);
        moduleRepo.save(module);

        if (streamProcessor.isRunning()) {
            streamProcessor.processModuleData(module);
        }

        result.put("success", true);
        result.put("message", "Membrane replacement recorded successfully");
        result.put("module", MODULE_NAME);
        result.put("moduleCode", moduleCode);
        result.put("serialNo", serialNo);
        result.put("operator", operator);
        result.put("newBaselineFlux", baseFlux);
        result.put("newBaselineTmd", baseTmd);
        result.put("warmupHours", config.getBaselineWarmupHours());

        log.info("[{}] Membrane replacement manually recorded for {} by {}: serialNo={}",
                MODULE_NAME, moduleCode, operator, serialNo);

        return result;
    }

    public Map<String, Object> getStreamStats() {
        return streamProcessor.getStreamStats();
    }

    public boolean startStreamProcessing() {
        if (streamProcessor.isRunning()) {
            return false;
        }
        List<MembraneModule> latestModules = new ArrayList<>();
        for (MembraneModule m : moduleRepo.findAllActive()) {
            List<MembraneModule> latest = moduleRepo.findLatestByModuleCode(m.getModuleCode());
            if (!latest.isEmpty()) {
                latestModules.add(latest.get(0));
            }
        }
        streamProcessor.startStreamProcessing(latestModules, config.getStreamParallelism());
        return streamProcessor.isRunning();
    }

    public boolean stopStreamProcessing() {
        if (!streamProcessor.isRunning()) {
            return false;
        }
        streamProcessor.stop();
        return !streamProcessor.isRunning();
    }
}
