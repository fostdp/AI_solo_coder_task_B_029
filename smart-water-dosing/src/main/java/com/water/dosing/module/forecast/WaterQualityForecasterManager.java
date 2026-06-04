package com.water.dosing.module.forecast;

import com.water.dosing.entity.WaterQuality;
import com.water.dosing.entity.WaterQualityPrediction;
import com.water.dosing.module.forecast.dto.WaterQualityPredictionResult;
import com.water.dosing.repository.WaterQualityPredictionRepository;
import com.water.dosing.repository.WaterQualityRepository;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WaterQualityForecasterManager {

    private static final Logger log = LoggerFactory.getLogger(WaterQualityForecasterManager.class);
    private static final String MODULE_NAME = "WaterQualityForecasterManager";
    private static final String MODULE_VERSION = "2.0.0";
    private static final String MODEL_VERSION = "ARIMA-v2.0";

    private final WaterQualityRepository waterQualityRepo;
    private final WaterQualityPredictionRepository predictionRepo;
    private final WaterQualityForecasterConfig config;

    private final Map<String, CompletableFuture<WaterQualityPredictionResult>> runningTasks = new ConcurrentHashMap<>();

    public WaterQualityForecasterManager(WaterQualityRepository waterQualityRepo,
                                          WaterQualityPredictionRepository predictionRepo,
                                          WaterQualityForecasterConfig config) {
        this.waterQualityRepo = waterQualityRepo;
        this.predictionRepo = predictionRepo;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        log.info("[{}] Initializing module version={}, asyncInference=true, mutationDetection={}",
                MODULE_NAME, MODULE_VERSION, config.isEnableMutationDetection());
    }

    public Map<String, Object> getModuleInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("moduleName", MODULE_NAME);
        info.put("version", MODULE_VERSION);
        info.put("modelVersion", MODEL_VERSION);
        info.put("status", "active");
        info.put("asyncInference", true);
        info.put("predictionHours", config.getPredictionHours());
        info.put("historyHours", config.getHistoryHours());
        info.put("mutationDetectionEnabled", config.isEnableMutationDetection());
        info.put("confidenceCalculationEnabled", config.isEnableConfidenceCalculation());
        info.put("mutationSensitivity", config.getMutationSensitivity());
        info.put("predictedIndicators", config.getPredictedIndicators());
        info.put("maxConcurrentPredictions", config.getMaxConcurrentPredictions());
        info.put("activeAsyncTasks", runningTasks.size());
        return info;
    }

    @Async("predictionExecutor")
    public CompletableFuture<WaterQualityPredictionResult> forecastAsync(String stage) {
        String taskId = "FORECAST-" + stage + "-" + System.currentTimeMillis();
        log.info("[{}] Starting async forecast for stage={}", MODULE_NAME, stage);

        CompletableFuture<WaterQualityPredictionResult> future = new CompletableFuture<>();
        runningTasks.put(taskId, future);

        try {
            WaterQualityPredictionResult result = executeForecast(stage);
            future.complete(result);
            log.info("[{}] Async forecast completed for stage={}, status={}", MODULE_NAME, stage, result.getStatus());
        } catch (Exception e) {
            log.error("[{}] Async forecast failed for stage={}: {}", MODULE_NAME, stage, e.getMessage(), e);
            WaterQualityPredictionResult errorResult = new WaterQualityPredictionResult();
            errorResult.setPredictionId(taskId);
            errorResult.setPredictionTime(Instant.now());
            errorResult.setStatus("error");
            future.complete(errorResult);
        } finally {
            runningTasks.remove(taskId);
        }

        return future;
    }

    public WaterQualityPredictionResult forecastSync(String stage) {
        return executeForecast(stage);
    }

    private WaterQualityPredictionResult executeForecast(String stage) {
        WaterQualityPredictionResult result = new WaterQualityPredictionResult();
        result.setPredictionId("PRED-" + stage.toUpperCase() + "-" + System.currentTimeMillis());
        result.setPredictionTime(Instant.now());
        result.setPredictionHours(config.getPredictionHours());
        result.setModelVersion(MODEL_VERSION);

        List<WaterQuality> history = getHistoryData(stage, config.getHistoryHours());
        result.setDataPointsUsed(history.size());

        if (history.size() < config.getMinDataPoints()) {
            log.warn("[{}] Insufficient data for forecast: {} points (min={})",
                    MODULE_NAME, history.size(), config.getMinDataPoints());
            result.setStatus("insufficient_data");
            result.setOverallConfidence(0.0);
            result.setPredictions(Collections.emptyList());
            return result;
        }

        List<WaterQualityPredictionResult.IndicatorPrediction> predictions = new ArrayList<>();
        List<String> allWarnings = new ArrayList<>();
        List<String> allAlarms = new ArrayList<>();

        double[] turbidityHistory = extractValues(history, WaterQuality::getTurbidity);
        double[] chlorineHistory = extractValues(history,
                wq -> wq.getResidualChlorine() != null ? wq.getResidualChlorine() : 0.5);
        double[] flowHistory = extractValues(history, WaterQuality::getFlowRate);
        double[] coagulantHistory = extractValues(history,
                wq -> wq.getCoagulantDose() != null ? wq.getCoagulantDose() : 10.0);
        double[] phHistory = extractValues(history, WaterQuality::getPh);
        double[] codHistory = extractValues(history, WaterQuality::getCod);
        double[] ammoniaHistory = extractValues(history, WaterQuality::getAmmonia);
        double[] conductivityHistory = extractValues(history,
                wq -> wq.getTemperature() != null ? wq.getTemperature() * 10 : 200);

        WaterQualityPredictionResult.MutationDetectionResult mutationResult = null;
        if (config.isEnableMutationDetection()) {
            mutationResult = detectMutations(turbidityHistory, chlorineHistory, phHistory, codHistory);
            result.setMutationDetection(mutationResult);
        }

        boolean rapidResponse = mutationResult != null && mutationResult.isMutationDetected();
        String rapidResponseReason = null;
        if (rapidResponse && mutationResult.getMutationEvents() != null) {
            rapidResponseReason = mutationResult.getMutationDetails();
        }

        int steps = config.getPredictionHours() * 12;

        if (config.getPredictedIndicators().contains("turbidity")) {
            WaterQualityPredictionResult.IndicatorPrediction turbPred =
                    forecastIndicator("turbidity", "浊度", turbidityHistory, steps,
                            0.5, null, rapidResponse, rapidResponseReason,
                            flowHistory, coagulantHistory);
            predictions.add(turbPred);
        }

        if (config.getPredictedIndicators().contains("residual_chlorine") ||
                config.getPredictedIndicators().contains("chlorine")) {
            WaterQualityPredictionResult.IndicatorPrediction clPred =
                    forecastIndicator("residual_chlorine", "余氯", chlorineHistory, steps,
                            null, 0.3, rapidResponse, rapidResponseReason, null, null);
            predictions.add(clPred);
        }

        if (config.getPredictedIndicators().contains("ph")) {
            WaterQualityPredictionResult.IndicatorPrediction phPred =
                    forecastIndicator("ph", "pH值", phHistory, steps,
                            9.0, 6.5, rapidResponse, rapidResponseReason, null, null);
            predictions.add(phPred);
        }

        if (config.getPredictedIndicators().contains("cod")) {
            WaterQualityPredictionResult.IndicatorPrediction codPred =
                    forecastIndicator("cod", "COD", codHistory, steps,
                            3.0, null, rapidResponse, rapidResponseReason, null, null);
            predictions.add(codPred);
        }

        if (config.getPredictedIndicators().contains("ammonia")) {
            WaterQualityPredictionResult.IndicatorPrediction ammoniaPred =
                    forecastIndicator("ammonia", "氨氮", ammoniaHistory, steps,
                            0.5, null, rapidResponse, rapidResponseReason, null, null);
            predictions.add(ammoniaPred);
        }

        if (config.getPredictedIndicators().contains("conductivity")) {
            WaterQualityPredictionResult.IndicatorPrediction condPred =
                    forecastIndicator("conductivity", "电导率", conductivityHistory, steps,
                            null, null, rapidResponse, rapidResponseReason, null, null);
            predictions.add(condPred);
        }

        result.setPredictions(predictions);

        double overallConfidence = predictions.stream()
                .mapToDouble(WaterQualityPredictionResult.IndicatorPrediction::getConfidence)
                .average()
                .orElse(config.getDefaultConfidenceLevel());
        result.setOverallConfidence(Math.round(overallConfidence * 100.0) / 100.0);

        result.setStatus(rapidResponse ? "rapid_response" : "normal");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rapidResponse", rapidResponse);
        metadata.put("rapidResponseReason", rapidResponseReason);
        metadata.put("forecastSteps", steps);
        metadata.put("historyDataPoints", history.size());
        metadata.put("mutationDetected", mutationResult != null && mutationResult.isMutationDetected());
        result.setModelMetadata(metadata);

        savePredictions(stage, predictions, rapidResponse, rapidResponseReason);

        log.info("[{}] Forecast complete: stage={}, predictions={}, overallConfidence={}, rapidResponse={}",
                MODULE_NAME, stage, predictions.size(), result.getOverallConfidence(), rapidResponse);

        return result;
    }

    private WaterQualityPredictionResult.IndicatorPrediction forecastIndicator(
            String code, String name, double[] history, int steps,
            Double upperThreshold, Double lowerThreshold,
            boolean rapidResponse, String rapidResponseReason,
            double[] flowHistory, double[] coagulantHistory) {

        WaterQualityPredictionResult.IndicatorPrediction pred = new WaterQualityPredictionResult.IndicatorPrediction();
        pred.setIndicatorCode(code);
        pred.setIndicatorName(name);

        double currentValue = history.length > 0 ? history[history.length - 1] : 0;
        pred.setCurrentValue(Math.round(currentValue * 1000.0) / 1000.0);

        double[] forecastValues;
        if (rapidResponse) {
            int rapidPoints = Math.min(history.length, 36);
            int direction = 1;
            forecastValues = forecastRapidResponse(history, steps, rapidPoints, direction);
        } else {
            forecastValues = forecastARIMA(history, steps);
        }

        double predictedValue = forecastValues.length > 0 ? forecastValues[forecastValues.length - 1] : currentValue;
        pred.setPredictedValue(Math.round(predictedValue * 1000.0) / 1000.0);

        double std = calculateStd(history);
        double margin = 1.96 * std;
        pred.setMinPredicted(Math.round((predictedValue - margin) * 1000.0) / 1000.0);
        pred.setMaxPredicted(Math.round((predictedValue + margin) * 1000.0) / 1000.0);

        if (config.isEnableConfidenceCalculation()) {
            double confidence = calculateConfidence(history);
            pred.setConfidence(Math.round(confidence * 100.0) / 100.0);
        } else {
            pred.setConfidence(config.getDefaultConfidenceLevel());
        }

        String trend = determineTrend(history);
        pred.setTrend(trend);

        double trendRate = calculateTrendRate(history);
        pred.setTrendRate(Math.round(trendRate * 10000.0) / 10000.0);

        String level = "normal";
        String remarks = null;
        if (upperThreshold != null && predictedValue > upperThreshold) {
            level = "alarm";
            remarks = String.format("%s预测值%.3f超过上限阈值%.3f", name, predictedValue, upperThreshold);
        } else if (lowerThreshold != null && predictedValue < lowerThreshold) {
            level = "alarm";
            remarks = String.format("%s预测值%.3f低于下限阈值%.3f", name, predictedValue, lowerThreshold);
        } else if (upperThreshold != null && predictedValue > upperThreshold * 0.85) {
            level = "warning";
            remarks = String.format("%s预测值%.3f接近上限阈值%.3f", name, predictedValue, upperThreshold);
        } else if (lowerThreshold != null && predictedValue < lowerThreshold * 1.15) {
            level = "warning";
            remarks = String.format("%s预测值%.3f接近下限阈值%.3f", name, predictedValue, lowerThreshold);
        }
        pred.setLevel(level);

        if (rapidResponse && rapidResponseReason != null) {
            remarks = (remarks != null ? remarks + "; " : "") + rapidResponseReason;
        }
        pred.setRemarks(remarks);

        double mean = Arrays.stream(history).average().orElse(1);
        double deviation = mean > 0 ? (predictedValue - mean) / mean : 0;
        pred.setDeviationFromNormal(Math.round(deviation * 10000.0) / 10000.0);

        List<WaterQualityPredictionResult.TimePoint> hourlyForecast = new ArrayList<>();
        Instant baseTime = Instant.now();
        int stepInterval = 5;
        int pointsPerHour = 60 / stepInterval;
        for (int i = 0; i < Math.min(forecastValues.length, config.getPredictionHours() * pointsPerHour); i++) {
            if (i % pointsPerHour == 0) {
                int hourIndex = i / pointsPerHour;
                Instant time = baseTime.plus(hourIndex + 1, ChronoUnit.HOURS);
                WaterQualityPredictionResult.TimePoint tp = new WaterQualityPredictionResult.TimePoint(time, forecastValues[i]);
                tp.setLowerBound(forecastValues[i] - margin);
                tp.setUpperBound(forecastValues[i] + margin);
                hourlyForecast.add(tp);
            }
        }
        pred.setHourlyForecast(hourlyForecast);

        return pred;
    }

    private WaterQualityPredictionResult.MutationDetectionResult detectMutations(
            double[] turbidity, double[] chlorine, double[] ph, double[] cod) {

        WaterQualityPredictionResult.MutationDetectionResult result =
                new WaterQualityPredictionResult.MutationDetectionResult();

        List<WaterQualityPredictionResult.MutationEvent> events = new ArrayList<>();
        double cusumThreshold = config.getMutationSensitivity() * 60;
        double cusumDrift = 0.5;

        CusumResult turbCusum = detectChangePointCUSUM(turbidity, cusumThreshold, cusumDrift);
        if (turbCusum.hasChangePoint) {
            WaterQualityPredictionResult.MutationEvent event = new WaterQualityPredictionResult.MutationEvent();
            event.setIndicator("turbidity");
            event.setTime(Instant.now());
            event.setCusumValue(Math.round(turbCusum.cusumValue * 100.0) / 100.0);
            event.setDeviation(Math.round(turbCusum.changeMagnitude * 100.0) / 100.0);
            event.setSeverity(turbCusum.changeMagnitude > 0.3 ? "high" : "medium");
            event.setDescription(String.format("浊度突变: 变化幅度%.1f%%, CUSUM=%.2f",
                    turbCusum.changeMagnitude * 100, turbCusum.cusumValue));
            events.add(event);
        }

        CusumResult clCusum = detectChangePointCUSUM(chlorine, cusumThreshold, cusumDrift);
        if (clCusum.hasChangePoint) {
            WaterQualityPredictionResult.MutationEvent event = new WaterQualityPredictionResult.MutationEvent();
            event.setIndicator("residual_chlorine");
            event.setTime(Instant.now());
            event.setCusumValue(Math.round(clCusum.cusumValue * 100.0) / 100.0);
            event.setDeviation(Math.round(clCusum.changeMagnitude * 100.0) / 100.0);
            event.setSeverity(clCusum.changeMagnitude > 0.3 ? "high" : "medium");
            event.setDescription(String.format("余氯突变: 变化幅度%.1f%%, CUSUM=%.2f",
                    clCusum.changeMagnitude * 100, clCusum.cusumValue));
            events.add(event);
        }

        CusumResult phCusum = detectChangePointCUSUM(ph, cusumThreshold, cusumDrift);
        if (phCusum.hasChangePoint) {
            WaterQualityPredictionResult.MutationEvent event = new WaterQualityPredictionResult.MutationEvent();
            event.setIndicator("ph");
            event.setTime(Instant.now());
            event.setCusumValue(Math.round(phCusum.cusumValue * 100.0) / 100.0);
            event.setDeviation(Math.round(phCusum.changeMagnitude * 100.0) / 100.0);
            event.setSeverity(phCusum.changeMagnitude > 0.2 ? "high" : "medium");
            event.setDescription(String.format("pH突变: 变化幅度%.1f%%, CUSUM=%.2f",
                    phCusum.changeMagnitude * 100, phCusum.cusumValue));
            events.add(event);
        }

        CusumResult codCusum = detectChangePointCUSUM(cod, cusumThreshold, cusumDrift);
        if (codCusum.hasChangePoint) {
            WaterQualityPredictionResult.MutationEvent event = new WaterQualityPredictionResult.MutationEvent();
            event.setIndicator("cod");
            event.setTime(Instant.now());
            event.setCusumValue(Math.round(codCusum.cusumValue * 100.0) / 100.0);
            event.setDeviation(Math.round(codCusum.changeMagnitude * 100.0) / 100.0);
            event.setSeverity(codCusum.changeMagnitude > 0.3 ? "high" : "medium");
            event.setDescription(String.format("COD突变: 变化幅度%.1f%%, CUSUM=%.2f",
                    codCusum.changeMagnitude * 100, codCusum.cusumValue));
            events.add(event);
        }

        result.setMutationDetected(!events.isEmpty());
        result.setMutationCount(events.size());
        result.setMutationEvents(events);
        result.setControlLimit(cusumThreshold);
        result.setSensitivity(config.getMutationSensitivity());

        if (!events.isEmpty()) {
            String details = events.stream()
                    .map(e -> e.getDescription())
                    .collect(Collectors.joining("; "));
            result.setMutationDetails(details);
        }

        return result;
    }

    @Transactional
    protected void savePredictions(String stage,
                                    List<WaterQualityPredictionResult.IndicatorPrediction> predictions,
                                    boolean rapidResponse, String rapidResponseReason) {
        Instant predictionTime = Instant.now();
        for (WaterQualityPredictionResult.IndicatorPrediction pred : predictions) {
            for (int hourOffset = 1; hourOffset <= config.getPredictionHours(); hourOffset++) {
                Instant targetTime = predictionTime.plus(hourOffset, ChronoUnit.HOURS);

                WaterQualityPrediction entity = new WaterQualityPrediction(predictionTime, targetTime, stage, pred.getIndicatorCode());
                entity.setPredictedValue(pred.getPredictedValue());
                entity.setLowerBound(pred.getMinPredicted());
                entity.setUpperBound(pred.getMaxPredicted());
                entity.setConfidence(pred.getConfidence());
                entity.setModelVersion(rapidResponse ? MODEL_VERSION + "-RAPID" : MODEL_VERSION);

                boolean isAlarm = "alarm".equals(pred.getLevel());
                boolean isWarning = "warning".equals(pred.getLevel());
                entity.setIsAlarm(isAlarm);
                entity.setIsWarning(isWarning);
                entity.setWarningLevel(pred.getLevel());
                entity.setRemarks(pred.getRemarks());

                if (pred.getIndicatorCode().equals("turbidity")) {
                    entity.setThresholdValue(0.5);
                    if (isAlarm) {
                        entity.setDosingAdjustment("建议增加混凝剂投加量");
                        entity.setProcessAdjustment("建议降低处理负荷，检查絮凝池运行状态");
                    } else if (isWarning) {
                        entity.setDosingAdjustment("建议监控混凝剂投加");
                        entity.setProcessAdjustment("建议关注原水水质变化");
                    }
                } else if (pred.getIndicatorCode().equals("residual_chlorine")) {
                    entity.setThresholdValue(0.3);
                    if (isAlarm) {
                        entity.setDosingAdjustment("建议立即调整消毒剂投加量");
                        entity.setProcessAdjustment("检查消毒剂投加系统运行状态");
                    } else if (isWarning) {
                        entity.setDosingAdjustment("建议持续监控余氯变化");
                        entity.setProcessAdjustment("关注水质pH和温度对消毒效果的影响");
                    }
                }

                predictionRepo.save(entity);
            }
        }
    }

    public List<Map<String, Object>> getLatestForecast(String stage) {
        List<WaterQualityPrediction> turbPredictions = predictionRepo.findLatestPredictions(stage, "turbidity");
        List<WaterQualityPrediction> clPredictions = predictionRepo.findLatestPredictions(stage, "residual_chlorine");

        Map<String, Map<String, Object>> byTime = new LinkedHashMap<>();

        for (WaterQualityPrediction p : turbPredictions) {
            String key = p.getTargetTime().toString();
            Map<String, Object> item = byTime.computeIfAbsent(key, k -> new LinkedHashMap<>());
            item.put("time", key);
            item.put("turbidity", p.getPredictedValue());
            item.put("turbidityLower", p.getLowerBound());
            item.put("turbidityUpper", p.getUpperBound());
            item.put("turbidityWarning", p.getWarningLevel());
            item.put("turbidityThreshold", p.getThresholdValue());
        }

        for (WaterQualityPrediction p : clPredictions) {
            String key = p.getTargetTime().toString();
            Map<String, Object> item = byTime.computeIfAbsent(key, k -> new LinkedHashMap<>());
            item.put("time", key);
            item.put("residualChlorine", p.getPredictedValue());
            item.put("chlorineLower", p.getLowerBound());
            item.put("chlorineUpper", p.getUpperBound());
            item.put("chlorineWarning", p.getWarningLevel());
            item.put("chlorineThreshold", p.getThresholdValue());
        }

        return new ArrayList<>(byTime.values());
    }

    public List<Map<String, Object>> getActiveWarnings() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        List<WaterQualityPrediction> warnings = predictionRepo.findActiveWarnings(start);

        List<Map<String, Object>> result = new ArrayList<>();
        for (WaterQualityPrediction p : warnings) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("parameter", p.getParameterName());
            item.put("predictedValue", p.getPredictedValue());
            item.put("threshold", p.getThresholdValue());
            item.put("targetTime", p.getTargetTime().toString());
            item.put("level", p.getWarningLevel());
            item.put("dosingAdjustment", p.getDosingAdjustment());
            item.put("processAdjustment", p.getProcessAdjustment());
            if (p.getRemarks() != null) {
                item.put("remarks", p.getRemarks());
            }
            result.add(item);
        }

        return result;
    }

    public Map<String, Object> getForecastSummary(String stage) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<WaterQualityPrediction> turbLatest = predictionRepo.findLatestPredictions(stage, "turbidity");
        List<WaterQualityPrediction> clLatest = predictionRepo.findLatestPredictions(stage, "residual_chlorine");

        boolean hasTurbAlarm = turbLatest.stream().anyMatch(p -> Boolean.TRUE.equals(p.getIsAlarm()));
        boolean hasTurbWarning = turbLatest.stream().anyMatch(p -> Boolean.TRUE.equals(p.getIsWarning()));
        boolean hasClAlarm = clLatest.stream().anyMatch(p -> Boolean.TRUE.equals(p.getIsAlarm()));
        boolean hasClWarning = clLatest.stream().anyMatch(p -> Boolean.TRUE.equals(p.getIsWarning()));

        result.put("module", MODULE_NAME);
        result.put("moduleVersion", MODULE_VERSION);
        result.put("modelVersion", MODEL_VERSION);
        result.put("stage", stage);
        result.put("hasAlarm", hasTurbAlarm || hasClAlarm);
        result.put("hasWarning", hasTurbWarning || hasClWarning);
        result.put("turbidityAlarm", hasTurbAlarm);
        result.put("turbidityWarning", hasTurbWarning);
        result.put("chlorineAlarm", hasClAlarm);
        result.put("chlorineWarning", hasClWarning);
        result.put("predictionHours", config.getPredictionHours());

        if (!turbLatest.isEmpty()) {
            WaterQualityPrediction last = turbLatest.get(turbLatest.size() - 1);
            result.put("maxTurbidityForecast", last.getPredictedValue());
            result.put("turbidityThreshold", last.getThresholdValue());
        }

        if (!clLatest.isEmpty()) {
            WaterQualityPrediction last = clLatest.get(clLatest.size() - 1);
            result.put("chlorineForecast", last.getPredictedValue());
            result.put("chlorineThreshold", last.getThresholdValue());
        }

        List<String> adjustments = new ArrayList<>();
        turbLatest.stream()
                .filter(p -> p.getDosingAdjustment() != null || p.getProcessAdjustment() != null)
                .findFirst()
                .ifPresent(p -> {
                    if (p.getDosingAdjustment() != null) adjustments.add(p.getDosingAdjustment());
                    if (p.getProcessAdjustment() != null) adjustments.add(p.getProcessAdjustment());
                });

        clLatest.stream()
                .filter(p -> p.getDosingAdjustment() != null || p.getProcessAdjustment() != null)
                .findFirst()
                .ifPresent(p -> {
                    if (p.getDosingAdjustment() != null) adjustments.add(p.getDosingAdjustment());
                    if (p.getProcessAdjustment() != null) adjustments.add(p.getProcessAdjustment());
                });

        result.put("suggestedAdjustments", adjustments);

        return result;
    }

    public int getRunningTaskCount() {
        return runningTasks.size();
    }

    private List<WaterQuality> getHistoryData(String stage, int hours) {
        Instant start = Instant.now().minus(hours, ChronoUnit.HOURS);
        return waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(stage, start, Instant.now());
    }

    private double[] extractValues(List<WaterQuality> history, Function<WaterQuality, Double> extractor) {
        return history.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .toArray();
    }

    private static class CusumResult {
        final boolean hasChangePoint;
        final int changePointIndex;
        final double changeMagnitude;
        final int changeDirection;
        final double cusumValue;

        CusumResult(boolean hasChangePoint, int changePointIndex,
                    double changeMagnitude, int changeDirection, double cusumValue) {
            this.hasChangePoint = hasChangePoint;
            this.changePointIndex = changePointIndex;
            this.changeMagnitude = changeMagnitude;
            this.changeDirection = changeDirection;
            this.cusumValue = cusumValue;
        }
    }

    private CusumResult detectChangePointCUSUM(double[] data, double threshold, double drift) {
        if (data == null || data.length < 10) {
            return new CusumResult(false, -1, 0, 0, 0);
        }

        int n = data.length;
        int windowSize = Math.min(20, n / 2);

        double baselineMean = 0;
        for (int i = 0; i < windowSize; i++) {
            baselineMean += data[i];
        }
        baselineMean /= windowSize;

        double baselineStd = 0;
        for (int i = 0; i < windowSize; i++) {
            baselineStd += (data[i] - baselineMean) * (data[i] - baselineMean);
        }
        baselineStd = Math.sqrt(baselineStd / windowSize);
        if (baselineStd < 0.01) baselineStd = 0.01;

        double cusumPos = 0;
        double cusumNeg = 0;
        double maxCusum = 0;
        int changePoint = -1;
        int direction = 0;
        double maxMagnitude = 0;

        double effectiveDrift = drift * baselineStd;

        for (int i = windowSize; i < n; i++) {
            double normalized = (data[i] - baselineMean) / baselineStd;

            cusumPos = Math.max(0, cusumPos + normalized - effectiveDrift);
            cusumNeg = Math.max(0, cusumNeg - normalized - effectiveDrift);

            double currentCusum = Math.max(cusumPos, cusumNeg);

            if (currentCusum > maxCusum) {
                maxCusum = currentCusum;
                if (currentCusum > threshold) {
                    changePoint = i;
                    direction = cusumPos > cusumNeg ? 1 : -1;
                    double recentMean = 0;
                    int recentCount = Math.min(5, n - i);
                    for (int j = i; j < i + recentCount; j++) {
                        recentMean += data[j];
                    }
                    recentMean /= recentCount;
                    maxMagnitude = Math.abs(recentMean - baselineMean) / Math.max(baselineMean, 0.01);
                }
            }
        }

        return new CusumResult(maxCusum > threshold, changePoint, maxMagnitude, direction, maxCusum);
    }

    private double[] forecastARIMA(double[] history, int steps) {
        int n = history.length;
        if (n < 3) {
            double[] result = new double[steps];
            double last = history[n - 1];
            Arrays.fill(result, last);
            return result;
        }

        double[] diff = new double[n - 1];
        for (int i = 1; i < n; i++) {
            diff[i - 1] = history[i] - history[i - 1];
        }

        double meanDiff = Arrays.stream(diff).average().orElse(0);
        double[] centeredDiff = Arrays.stream(diff).map(d -> d - meanDiff).toArray();

        double[] arCoeffs = fitAR(centeredDiff, 3);

        double[] forecastDiff = new double[steps];
        double[] recent = Arrays.copyOfRange(centeredDiff, centeredDiff.length - 3, centeredDiff.length);

        for (int i = 0; i < steps; i++) {
            double pred = 0;
            for (int j = 0; j < arCoeffs.length; j++) {
                pred += arCoeffs[j] * recent[recent.length - 1 - j];
            }
            forecastDiff[i] = pred + meanDiff;

            for (int j = 0; j < recent.length - 1; j++) {
                recent[j] = recent[j + 1];
            }
            recent[recent.length - 1] = pred;
        }

        double[] forecast = new double[steps];
        double lastValue = history[n - 1];
        for (int i = 0; i < steps; i++) {
            lastValue += forecastDiff[i];
            forecast[i] = lastValue;
        }

        return forecast;
    }

    private double[] fitAR(double[] data, int p) {
        int n = data.length;
        if (n < p + 1) {
            double[] coeffs = new double[Math.min(p, n)];
            Arrays.fill(coeffs, 0.1);
            return coeffs;
        }

        double[][] X = new double[n - p][p];
        double[] Y = new double[n - p];

        for (int i = 0; i < n - p; i++) {
            for (int j = 0; j < p; j++) {
                X[i][j] = data[i + j];
            }
            Y[i] = data[i + p];
        }

        double[][] XtX = new double[p][p];
        double[] XtY = new double[p];

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                for (int k = 0; k < n - p; k++) {
                    XtX[i][j] += X[k][i] * X[k][j];
                }
            }
            for (int k = 0; k < n - p; k++) {
                XtY[i] += X[k][i] * Y[k];
            }
            XtX[i][i] += 0.001;
        }

        return solveLinearSystem(XtX, XtY);
    }

    private double[] solveLinearSystem(double[][] A, double[] b) {
        int n = b.length;
        double[][] aug = new double[n][n + 1];

        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n] = b[i];
        }

        for (int i = 0; i < n; i++) {
            int maxRow = i;
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(aug[j][i]) > Math.abs(aug[maxRow][i])) {
                    maxRow = j;
                }
            }
            double[] temp = aug[i];
            aug[i] = aug[maxRow];
            aug[maxRow] = temp;

            double pivot = aug[i][i];
            if (Math.abs(pivot) < 1e-10) continue;
            for (int j = i; j <= n; j++) {
                aug[i][j] /= pivot;
            }

            for (int j = 0; j < n; j++) {
                if (j != i) {
                    double factor = aug[j][i];
                    for (int k = i; k <= n; k++) {
                        aug[j][k] -= factor * aug[i][k];
                    }
                }
            }
        }

        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            result[i] = aug[i][n];
        }
        return result;
    }

    private double[] forecastRapidResponse(double[] history, int steps, int recentPoints, int direction) {
        int n = history.length;
        double[] result = new double[steps];

        if (n < recentPoints || recentPoints < 2) {
            double last = history[n - 1];
            Arrays.fill(result, last);
            return result;
        }

        double[] recent = Arrays.copyOfRange(history, n - recentPoints, n);

        double[] weights = new double[recentPoints];
        double totalWeight = 0;
        for (int i = 0; i < recentPoints; i++) {
            weights[i] = Math.pow(1.5, i);
            totalWeight += weights[i];
        }

        double weightedMean = 0;
        for (int i = 0; i < recentPoints; i++) {
            weightedMean += weights[i] * recent[i];
        }
        weightedMean /= totalWeight;

        double weightedSlope = 0;
        double xMean = (recentPoints - 1) / 2.0;
        double numerator = 0, denominator = 0;
        for (int i = 0; i < recentPoints; i++) {
            double xd = i - xMean;
            numerator += weights[i] * xd * (recent[i] - weightedMean);
            denominator += weights[i] * xd * xd;
        }
        if (denominator > 0) {
            weightedSlope = numerator / denominator;
        }

        double dampingFactor = Math.max(0.3, 1.0 - Math.abs(direction) * 0.3);
        double lastValue = recent[recentPoints - 1];

        for (int i = 0; i < steps; i++) {
            double decay = Math.pow(dampingFactor, i);
            double trend = weightedSlope * (i + 1) * decay;
            result[i] = Math.max(0, lastValue + trend);

            if (result[i] > lastValue * 5) {
                result[i] = lastValue * 5;
            }
            if (result[i] < lastValue * 0.2) {
                result[i] = lastValue * 0.2;
            }
        }

        return result;
    }

    private double calculateStd(double[] data) {
        if (data.length < 2) return 1.0;
        double mean = Arrays.stream(data).average().orElse(0);
        double variance = Arrays.stream(data).map(x -> (x - mean) * (x - mean)).average().orElse(1);
        return Math.sqrt(variance);
    }

    private double calculateConfidence(double[] data) {
        if (data.length < 5) return 0.7;
        double std = calculateStd(data);
        double mean = Arrays.stream(data).average().orElse(1);
        double cv = mean > 0 ? std / mean : 1;
        return Math.max(0.5, Math.min(0.95, 1 - cv * 0.5));
    }

    private String determineTrend(double[] data) {
        if (data.length < 5) return "stable";
        int recentSize = Math.min(10, data.length / 2);
        double[] recent = Arrays.copyOfRange(data, data.length - recentSize, data.length);
        double[] earlier = Arrays.copyOfRange(data, 0, recentSize);

        double recentMean = Arrays.stream(recent).average().orElse(0);
        double earlierMean = Arrays.stream(earlier).average().orElse(0);

        double change = earlierMean > 0 ? (recentMean - earlierMean) / earlierMean : 0;

        if (change > 0.05) return "rising";
        else if (change < -0.05) return "falling";
        else return "stable";
    }

    private double calculateTrendRate(double[] data) {
        if (data.length < 3) return 0;
        int n = data.length;
        double xMean = (n - 1) / 2.0;
        double yMean = Arrays.stream(data).average().orElse(0);
        double numerator = 0, denominator = 0;
        for (int i = 0; i < n; i++) {
            numerator += (i - xMean) * (data[i] - yMean);
            denominator += (i - xMean) * (i - xMean);
        }
        return denominator > 0 ? numerator / denominator : 0;
    }
}
