package com.water.dosing.predictor;

import com.water.dosing.entity.WaterQuality;
import com.water.dosing.entity.WaterQualityPrediction;
import com.water.dosing.repository.WaterQualityPredictionRepository;
import com.water.dosing.repository.WaterQualityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class WaterQualityForecaster {

    private static final Logger log = LoggerFactory.getLogger(WaterQualityForecaster.class);

    private final WaterQualityRepository waterQualityRepo;
    private final WaterQualityPredictionRepository predictionRepo;

    @Value("${prediction.turbidity-threshold:0.5}")
    private double turbidityThreshold;

    @Value("${prediction.residual-chlorine-min:0.3}")
    private double residualChlorineMin;

    @Value("${prediction.residual-chlorine-max:0.8}")
    private double residualChlorineMax;

    @Value("${prediction.lookback-hours:24}")
    private int lookbackHours;

    @Value("${prediction.forecast-minutes:60}")
    private int forecastMinutes;

    @Value("${prediction.cusum-threshold:3.0}")
    private double cusumThreshold;

    @Value("${prediction.cusum-drift:0.5}")
    private double cusumDrift;

    @Value("${prediction.rapid-response-enabled:true}")
    private boolean rapidResponseEnabled;

    @Value("${prediction.rapid-response-window-minutes:15}")
    private int rapidResponseWindowMinutes;

    private final String modelVersion = "ARIMA-v1.0";

    public WaterQualityForecaster(WaterQualityRepository waterQualityRepo,
                                   WaterQualityPredictionRepository predictionRepo) {
        this.waterQualityRepo = waterQualityRepo;
        this.predictionRepo = predictionRepo;
    }

    public static class ForecastResult {
        private final List<WaterQualityPrediction> predictions;
        private final boolean hasWarning;
        private final boolean hasAlarm;
        private final List<String> warnings;
        private final List<String> alarms;

        public ForecastResult(List<WaterQualityPrediction> predictions,
                               boolean hasWarning, boolean hasAlarm,
                               List<String> warnings, List<String> alarms) {
            this.predictions = predictions;
            this.hasWarning = hasWarning;
            this.hasAlarm = hasAlarm;
            this.warnings = warnings;
            this.alarms = alarms;
        }

        public List<WaterQualityPrediction> getPredictions() { return predictions; }
        public boolean isHasWarning() { return hasWarning; }
        public boolean isHasAlarm() { return hasAlarm; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getAlarms() { return alarms; }
    }

    public ForecastResult forecastOutletQuality() {
        List<WaterQuality> history = getHistoryData("outlet", lookbackHours);

        if (history.size() < 10) {
            log.warn("Insufficient history data for forecasting: {} points", history.size());
            return new ForecastResult(Collections.emptyList(), false, false,
                    Collections.emptyList(), Collections.emptyList());
        }

        Instant predictionTime = Instant.now();
        List<WaterQualityPrediction> allPredictions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> alarms = new ArrayList<>();

        double[] turbidityHistory = extractValues(history, WaterQuality::getTurbidity);
        double[] chlorineHistory = extractValues(history, wq -> wq.getResidualChlorine() != null ? wq.getResidualChlorine() : 0.5);
        double[] flowHistory = extractValues(history, WaterQuality::getFlowRate);
        double[] coagulantHistory = extractValues(history, wq -> wq.getCoagulantDose() != null ? wq.getCoagulantDose() : 10.0);

        CusumResult turbidityCusum = detectChangePointCUSUM(turbidityHistory, cusumThreshold, cusumDrift);
        CusumResult chlorineCusum = detectChangePointCUSUM(chlorineHistory, cusumThreshold, cusumDrift);

        boolean rapidResponse = false;
        String rapidResponseReason = null;
        if (rapidResponseEnabled) {
            if (turbidityCusum.hasChangePoint) {
                rapidResponse = true;
                rapidResponseReason = String.format("原水浊度突变检测: 变化幅度%.1f%%, 建议快速响应",
                        turbidityCusum.changeMagnitude * 100);
                log.warn(rapidResponseReason);
            }
            if (chlorineCusum.hasChangePoint) {
                rapidResponse = true;
                rapidResponseReason = String.format("余氯突变检测: 变化幅度%.1f%%, 建议快速响应",
                        chlorineCusum.changeMagnitude * 100);
                log.warn(rapidResponseReason);
            }
        }

        int steps = forecastMinutes / 5;

        double[] turbidityForecast;
        double[] chlorineForecast;
        if (rapidResponse) {
            int rapidPoints = Math.min(turbidityHistory.length, rapidResponseWindowMinutes / 5);
            turbidityForecast = forecastRapidResponse(turbidityHistory, steps, rapidPoints,
                    turbidityCusum.changeDirection);
            chlorineForecast = forecastRapidResponse(chlorineHistory, steps, rapidPoints,
                    chlorineCusum.changeDirection);
        } else {
            turbidityForecast = forecastARIMA(turbidityHistory, steps);
            chlorineForecast = forecastARIMA(chlorineHistory, steps);
        }

        double turbidityStd = calculateStd(turbidityHistory);
        double chlorineStd = calculateStd(chlorineHistory);

        for (int i = 0; i < steps; i++) {
            Instant targetTime = predictionTime.plus((i + 1) * 5, ChronoUnit.MINUTES);

            double turbidityPred = Math.max(0, turbidityForecast[i]);
            double chlorinePred = Math.max(0, Math.min(2.0, chlorineForecast[i]));

            double turbidityConfidence = calculateConfidence(turbidityHistory);
            double chlorineConfidence = calculateConfidence(chlorineHistory);

            WaterQualityPrediction turbPred = createPrediction(predictionTime, targetTime,
                    "outlet", "turbidity", turbidityPred, turbidityStd, turbidityConfidence,
                    turbidityThreshold, null);

            WaterQualityPrediction clPred = createPrediction(predictionTime, targetTime,
                    "outlet", "residual_chlorine", chlorinePred, chlorineStd, chlorineConfidence,
                    residualChlorineMax, residualChlorineMin);

            evaluateTurbidityWarning(turbPred, warnings, alarms, flowHistory, coagulantHistory);
            evaluateChlorineWarning(clPred, warnings, alarms);

            if (rapidResponse) {
                turbPred.setModelVersion(modelVersion + "-RAPID");
                clPred.setModelVersion(modelVersion + "-RAPID");
                turbPred.setRemarks(rapidResponseReason);
                clPred.setRemarks(rapidResponseReason);
            }

            allPredictions.add(turbPred);
            allPredictions.add(clPred);

            predictionRepo.save(turbPred);
            predictionRepo.save(clPred);
        }

        log.info("Forecast complete: {} predictions, warnings={}, alarms={}, rapidResponse={}",
                allPredictions.size(), warnings.size(), alarms.size(), rapidResponse);

        return new ForecastResult(allPredictions,
                !warnings.isEmpty(), !alarms.isEmpty(), warnings, alarms);
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

        return new CusumResult(
                maxCusum > threshold,
                changePoint,
                maxMagnitude,
                direction,
                maxCusum
        );
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

    private List<WaterQuality> getHistoryData(String stage, int hours) {
        Instant start = Instant.now().minus(hours, ChronoUnit.HOURS);
        return waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(stage, start, Instant.now());
    }

    private double[] extractValues(List<WaterQuality> history, java.util.function.Function<WaterQuality, Double> extractor) {
        return history.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .toArray();
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

    private WaterQualityPrediction createPrediction(Instant predictionTime, Instant targetTime,
                                                      String stage, String paramName,
                                                      double predictedValue, double std, double confidence,
                                                      Double upperThreshold, Double lowerThreshold) {
        WaterQualityPrediction pred = new WaterQualityPrediction(predictionTime, targetTime, stage, paramName);
        pred.setPredictedValue(Math.round(predictedValue * 1000.0) / 1000.0);
        pred.setLowerBound(Math.round((predictedValue - 1.96 * std) * 1000.0) / 1000.0);
        pred.setUpperBound(Math.round((predictedValue + 1.96 * std) * 1000.0) / 1000.0);
        pred.setConfidence(Math.round(confidence * 100.0) / 100.0);
        pred.setModelVersion(modelVersion);

        if (upperThreshold != null) {
            pred.setThresholdValue(upperThreshold);
            pred.setIsAlarm(predictedValue > upperThreshold);
            pred.setIsWarning(predictedValue > upperThreshold * 0.85);
        } else if (lowerThreshold != null) {
            pred.setThresholdValue(lowerThreshold);
            pred.setIsAlarm(predictedValue < lowerThreshold);
            pred.setIsWarning(predictedValue < lowerThreshold * 1.15);
        }

        if (pred.getIsAlarm()) {
            pred.setWarningLevel("alarm");
        } else if (pred.getIsWarning()) {
            pred.setWarningLevel("warning");
        } else {
            pred.setWarningLevel("normal");
        }

        return pred;
    }

    private void evaluateTurbidityWarning(WaterQualityPrediction pred, List<String> warnings, List<String> alarms,
                                           double[] flowHistory, double[] coagulantHistory) {
        if (!pred.getIsAlarm() && !pred.getIsWarning()) return;

        String param = pred.getParameterName();
        double predicted = pred.getPredictedValue();
        double threshold = pred.getThresholdValue();
        String timeStr = pred.getTargetTime().toString().substring(11, 16);

        if (pred.getIsAlarm()) {
            String alarmMsg = String.format("预测%s %s 时浊度 %.2f NTU 超限(阈值%.2f)", param, timeStr, predicted, threshold);
            alarms.add(alarmMsg);

            double avgFlow = Arrays.stream(flowHistory).average().orElse(8000);
            double avgCoagulant = Arrays.stream(coagulantHistory).average().orElse(10);
            double suggestedIncrease = Math.min(50, (predicted - threshold) / threshold * 100);

            String dosingAdj = String.format("建议混凝剂投加量增加%.0f%% (当前约%.1f→建议%.1f mg/L)",
                    suggestedIncrease, avgCoagulant, avgCoagulant * (1 + suggestedIncrease / 100));
            pred.setDosingAdjustment(dosingAdj);

            String processAdj = String.format("建议考虑: 1) 降低处理负荷至%.0f m³/h以下, 2) 检查絮凝池运行状态, 3) 确认药剂有效性",
                    avgFlow * 0.9);
            pred.setProcessAdjustment(processAdj);
        } else if (pred.getIsWarning()) {
            String warningMsg = String.format("预测%s %s 时浊度 %.2f NTU 接近阈值(%.2f)", param, timeStr, predicted, threshold);
            warnings.add(warningMsg);

            double avgCoagulant = Arrays.stream(coagulantHistory).average().orElse(10);
            pred.setDosingAdjustment(String.format("建议监控混凝剂投加，必要时微调(当前约%.1f mg/L)", avgCoagulant));
            pred.setProcessAdjustment("建议关注原水水质变化，提前准备工艺调整");
        }
    }

    private void evaluateChlorineWarning(WaterQualityPrediction pred, List<String> warnings, List<String> alarms) {
        if (!pred.getIsAlarm() && !pred.getIsWarning()) return;

        double predicted = pred.getPredictedValue();
        String timeStr = pred.getTargetTime().toString().substring(11, 16);

        if (pred.getIsAlarm()) {
            String alarmMsg;
            if (predicted < residualChlorineMin) {
                alarmMsg = String.format("预测出厂水 %s 时余氯 %.2f mg/L 低于下限(%.2f)", timeStr, predicted, residualChlorineMin);
                pred.setDosingAdjustment("建议立即增加消毒剂投加量，确保管网余氯达标");
                pred.setProcessAdjustment("检查消毒剂投加系统运行状态，确认投加点位置");
            } else {
                alarmMsg = String.format("预测出厂水 %s 时余氯 %.2f mg/L 高于上限(%.2f)", timeStr, predicted, residualChlorineMax);
                pred.setDosingAdjustment("建议适当减少消毒剂投加量，避免消毒副产物超标");
                pred.setProcessAdjustment("检查清水池停留时间，考虑优化投加点");
            }
            alarms.add(alarmMsg);
        } else if (pred.getIsWarning()) {
            String warningMsg = String.format("预测出厂水 %s 时余氯 %.2f mg/L 接近阈值", timeStr, predicted);
            warnings.add(warningMsg);
            pred.setDosingAdjustment("建议持续监控余氯变化，适时微调投加量");
            pred.setProcessAdjustment("关注水质pH和温度对消毒效果的影响");
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

        result.put("stage", stage);
        result.put("hasAlarm", hasTurbAlarm || hasClAlarm);
        result.put("hasWarning", hasTurbWarning || hasClWarning);
        result.put("turbidityAlarm", hasTurbAlarm);
        result.put("turbidityWarning", hasTurbWarning);
        result.put("chlorineAlarm", hasClAlarm);
        result.put("chlorineWarning", hasClWarning);
        result.put("forecastMinutes", forecastMinutes);
        result.put("modelVersion", modelVersion);

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
}
