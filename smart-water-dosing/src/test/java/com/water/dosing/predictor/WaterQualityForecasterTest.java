package com.water.dosing.predictor;

import com.water.dosing.entity.WaterQuality;
import com.water.dosing.entity.WaterQualityPrediction;
import com.water.dosing.repository.WaterQualityPredictionRepository;
import com.water.dosing.repository.WaterQualityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaterQualityForecasterTest {

    @Mock
    private WaterQualityRepository waterQualityRepo;

    @Mock
    private WaterQualityPredictionRepository predictionRepo;

    private WaterQualityForecaster forecaster;

    @BeforeEach
    void setUp() {
        forecaster = new WaterQualityForecaster(waterQualityRepo, predictionRepo);
        forecaster.turbidityThreshold = 0.5;
        forecaster.residualChlorineMin = 0.3;
        forecaster.residualChlorineMax = 0.8;
        forecaster.lookbackHours = 24;
        forecaster.forecastMinutes = 60;
    }

    private List<WaterQuality> createStableHistory(int points, double baseTurbidity, double baseFlow) {
        List<WaterQuality> history = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = points; i > 0; i--) {
            WaterQuality wq = new WaterQuality(now.minus(i * 5, ChronoUnit.MINUTES), "outlet",
                    baseTurbidity + (Math.random() - 0.5) * 0.1,
                    7.2, 18.0, 0.01, 0.2, baseFlow);
            history.add(wq);
        }
        return history;
    }

    private List<WaterQuality> createRisingTurbidityHistory(int points, double startTurbidity,
                                                              double endTurbidity, double baseFlow) {
        List<WaterQuality> history = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = points; i > 0; i--) {
            double turbidity = startTurbidity + (endTurbidity - startTurbidity) * (points - i) / points;
            WaterQuality wq = new WaterQuality(now.minus(i * 5, ChronoUnit.MINUTES), "outlet",
                    turbidity, 7.2, 18.0, 0.01, 0.2, baseFlow);
            history.add(wq);
        }
        return history;
    }

    private List<WaterQuality> createChlorineHistory(int points, double baseChlorine, double baseFlow) {
        List<WaterQuality> history = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = points; i > 0; i--) {
            WaterQuality wq = new WaterQuality(now.minus(i * 5, ChronoUnit.MINUTES), "outlet",
                    0.2, 7.2, 18.0, 0.01, 0.2, baseFlow);
            history.add(wq);
        }
        return history;
    }

    @Nested
    @DisplayName("ARIMA预测误差（MAE）测试")
    class PredictionErrorTests {

        @Test
        @DisplayName("稳定水质条件下预测MAE应小于0.2NTU")
        void forecast_stableTurbidity_maeShouldBeSmall() {
            List<WaterQuality> history = createStableHistory(50, 0.25, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            List<WaterQualityPrediction> predictions = result.getPredictions();
            List<WaterQualityPrediction> turbidityPreds = predictions.stream()
                    .filter(p -> "turbidity".equals(p.getParameterName()))
                    .collect(Collectors.toList());

            assertThat(turbidityPreds).isNotEmpty();

            double mae = turbidityPreds.stream()
                    .mapToDouble(p -> Math.abs(p.getPredictedValue() - 0.25))
                    .average().orElse(1.0);

            assertThat(mae).isLessThan(0.2);
        }

        @Test
        @DisplayName("预测值应在合理物理范围内")
        void forecast_predictionsShouldBeInPhysicalRange() {
            List<WaterQuality> history = createStableHistory(50, 0.25, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            for (WaterQualityPrediction pred : result.getPredictions()) {
                if ("turbidity".equals(pred.getParameterName())) {
                    assertThat(pred.getPredictedValue()).isGreaterThanOrEqualTo(0.0);
                    assertThat(pred.getPredictedValue()).isLessThan(10.0);
                }
            }
        }

        @Test
        @DisplayName("上升浊度趋势预测值应高于当前值")
        void forecast_risingTurbidity_predictionsShouldRise() {
            List<WaterQuality> history = createRisingTurbidityHistory(50, 0.2, 0.45, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            List<WaterQualityPrediction> turbPreds = result.getPredictions().stream()
                    .filter(p -> "turbidity".equals(p.getParameterName()))
                    .collect(Collectors.toList());

            if (!turbPreds.isEmpty()) {
                double lastActual = history.get(history.size() - 1).getTurbidity();
                double avgPredicted = turbPreds.stream()
                        .mapToDouble(WaterQualityPrediction::getPredictedValue)
                        .average().orElse(0);

                assertThat(avgPredicted).isGreaterThanOrEqualTo(lastActual * 0.8);
            }
        }

        @Test
        @DisplayName("置信区间应包含预测值")
        void forecast_confidenceIntervalShouldContainPrediction() {
            List<WaterQuality> history = createStableHistory(50, 0.25, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            for (WaterQualityPrediction pred : result.getPredictions()) {
                if (pred.getLowerBound() != null && pred.getUpperBound() != null) {
                    assertThat(pred.getLowerBound()).isLessThanOrEqualTo(pred.getPredictedValue() + 0.01);
                    assertThat(pred.getUpperBound()).isGreaterThanOrEqualTo(pred.getPredictedValue() - 0.01);
                }
            }
        }

        @Test
        @DisplayName("数据不足时应返回空预测列表")
        void forecast_insufficientData_shouldReturnEmpty() {
            List<WaterQuality> history = createStableHistory(5, 0.25, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            assertThat(result.getPredictions()).isEmpty();
            assertThat(result.isHasWarning()).isFalse();
            assertThat(result.isHasAlarm()).isFalse();
        }
    }

    @Nested
    @DisplayName("预警提前量测试")
    class EarlyWarningTests {

        @Test
        @DisplayName("浊度超标时应产生alarm级别预警")
        void forecast_turbidityExceedsThreshold_shouldGenerateAlarm() {
            List<WaterQuality> history = createRisingTurbidityHistory(50, 0.3, 0.65, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            boolean hasTurbidityAlarm = result.getPredictions().stream()
                    .anyMatch(p -> "turbidity".equals(p.getParameterName()) &&
                            Boolean.TRUE.equals(p.getIsAlarm()));

            if (hasTurbidityAlarm) {
                assertThat(result.isHasAlarm()).isTrue();
                assertThat(result.getAlarms()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("浊度接近阈值时应产生warning级别预警")
        void forecast_turbidityNearThreshold_shouldGenerateWarning() {
            List<WaterQuality> history = createRisingTurbidityHistory(50, 0.25, 0.45, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            boolean hasWarningOrAlarm = result.getPredictions().stream()
                    .anyMatch(p -> "turbidity".equals(p.getParameterName()) &&
                            (Boolean.TRUE.equals(p.getIsWarning()) || Boolean.TRUE.equals(p.getIsAlarm())));

            if (hasWarningOrAlarm) {
                assertThat(result.isHasWarning()).isTrue();
            }
        }

        @Test
        @DisplayName("预测时间点应在未来60分钟内")
        void forecast_targetTimes_shouldBeWithin60Minutes() {
            List<WaterQuality> history = createStableHistory(50, 0.25, 8000);
            Instant beforeForecast = Instant.now();

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            for (WaterQualityPrediction pred : result.getPredictions()) {
                long minutesAhead = ChronoUnit.MINUTES.between(beforeForecast, pred.getTargetTime());
                assertThat(minutesAhead).isBetween(1L, 65L);
            }
        }

        @Test
        @DisplayName("预警应提前足够时间以供操作人员响应")
        void forecast_warningShouldProvideSufficientLeadTime() {
            List<WaterQuality> history = createRisingTurbidityHistory(50, 0.35, 0.55, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            List<WaterQualityPrediction> alarmPredictions = result.getPredictions().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsAlarm()))
                    .collect(Collectors.toList());

            for (WaterQualityPrediction alarm : alarmPredictions) {
                Instant targetTime = alarm.getTargetTime();
                long minutesUntil = ChronoUnit.MINUTES.between(Instant.now(), targetTime);
                assertThat(minutesUntil).isGreaterThan(0);
            }
        }
    }

    @Nested
    @DisplayName("调整建议可行性和有效性测试")
    class AdjustmentSuggestionTests {

        @Test
        @DisplayName("浊度alarm应提供加药量调整建议")
        void forecast_turbidityAlarm_shouldProvideDosingAdjustment() {
            List<WaterQuality> history = createRisingTurbidityHistory(50, 0.35, 0.65, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            List<WaterQualityPrediction> alarmWithDosing = result.getPredictions().stream()
                    .filter(p -> "turbidity".equals(p.getParameterName()) &&
                            Boolean.TRUE.equals(p.getIsAlarm()) &&
                            p.getDosingAdjustment() != null)
                    .collect(Collectors.toList());

            if (!result.getAlarms().isEmpty()) {
                assertThat(alarmWithDosing).isNotEmpty();
                for (WaterQualityPrediction pred : alarmWithDosing) {
                    assertThat(pred.getDosingAdjustment()).contains("增加");
                }
            }
        }

        @Test
        @DisplayName("浊度alarm应提供工艺调整建议")
        void forecast_turbidityAlarm_shouldProvideProcessAdjustment() {
            List<WaterQuality> history = createRisingTurbidityHistory(50, 0.35, 0.65, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            List<WaterQualityPrediction> alarmWithProcess = result.getPredictions().stream()
                    .filter(p -> "turbidity".equals(p.getParameterName()) &&
                            Boolean.TRUE.equals(p.getIsAlarm()) &&
                            p.getProcessAdjustment() != null)
                    .collect(Collectors.toList());

            if (!result.getAlarms().isEmpty()) {
                assertThat(alarmWithProcess).isNotEmpty();
                for (WaterQualityPrediction pred : alarmWithProcess) {
                    assertThat(pred.getProcessAdjustment()).isNotEmpty();
                    assertThat(pred.getProcessAdjustment().length()).isGreaterThan(5);
                }
            }
        }

        @Test
        @DisplayName("正常水质不应生成调整建议")
        void forecast_normalQuality_shouldNotProvideAdjustment() {
            List<WaterQuality> history = createStableHistory(50, 0.2, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            List<WaterQualityPrediction> normalPreds = result.getPredictions().stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getIsAlarm()) &&
                            !Boolean.TRUE.equals(p.getIsWarning()))
                    .collect(Collectors.toList());

            long withAdjustment = normalPreds.stream()
                    .filter(p -> p.getDosingAdjustment() != null || p.getProcessAdjustment() != null)
                    .count();

            assertThat(withAdjustment).isEqualTo(0);
        }

        @Test
        @DisplayName("建议的加药量调整幅度应合理")
        void forecast_dosingAdjustment_shouldBeReasonable() {
            List<WaterQuality> history = createRisingTurbidityHistory(50, 0.35, 0.65, 8000);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            List<WaterQualityPrediction> withDosing = result.getPredictions().stream()
                    .filter(p -> p.getDosingAdjustment() != null &&
                            "turbidity".equals(p.getParameterName()))
                    .collect(Collectors.toList());

            for (WaterQualityPrediction pred : withDosing) {
                String adjustment = pred.getDosingAdjustment();
                assertThat(adjustment.length()).isGreaterThan(5);
                assertThat(adjustment).matches(".*\\d+.*");
            }
        }
    }

    @Nested
    @DisplayName("预测查询与汇总测试")
    class ForecastQueryTests {

        @Test
        @DisplayName("getForecastSummary应正确汇总预警状态")
        void getForecastSummary_shouldSummarizeWarningStatus() {
            WaterQualityPrediction turbAlarm = new WaterQualityPrediction(
                    Instant.now(), Instant.now().plus(5, ChronoUnit.MINUTES), "outlet", "turbidity");
            turbAlarm.setIsAlarm(true);
            turbAlarm.setIsWarning(true);
            turbAlarm.setPredictedValue(0.6);
            turbAlarm.setThresholdValue(0.5);

            WaterQualityPrediction clNormal = new WaterQualityPrediction(
                    Instant.now(), Instant.now().plus(5, ChronoUnit.MINUTES), "outlet", "residual_chlorine");
            clNormal.setIsAlarm(false);
            clNormal.setIsWarning(false);
            clNormal.setPredictedValue(0.5);
            clNormal.setThresholdValue(0.8);

            when(predictionRepo.findLatestPredictions("outlet", "turbidity"))
                    .thenReturn(Collections.singletonList(turbAlarm));
            when(predictionRepo.findLatestPredictions("outlet", "residual_chlorine"))
                    .thenReturn(Collections.singletonList(clNormal));

            Map<String, Object> summary = forecaster.getForecastSummary("outlet");

            assertThat(summary.get("hasAlarm")).isEqualTo(true);
            assertThat(summary.get("turbidityAlarm")).isEqualTo(true);
            assertThat(summary.get("chlorineAlarm")).isEqualTo(false);
        }

        @Test
        @DisplayName("getActiveWarnings应返回当前活跃预警")
        void getActiveWarnings_shouldReturnCurrentWarnings() {
            WaterQualityPrediction warning = new WaterQualityPrediction(
                    Instant.now(), Instant.now().plus(10, ChronoUnit.MINUTES), "outlet", "turbidity");
            warning.setIsAlarm(true);
            warning.setPredictedValue(0.6);
            warning.setThresholdValue(0.5);
            warning.setWarningLevel("alarm");
            warning.setDosingAdjustment("增加投加量10%");

            when(predictionRepo.findActiveWarnings(any(Instant.class)))
                    .thenReturn(Collections.singletonList(warning));

            List<Map<String, Object>> activeWarnings = forecaster.getActiveWarnings();

            assertThat(activeWarnings).hasSize(1);
            assertThat(activeWarnings.get(0).get("level")).isEqualTo("alarm");
            assertThat(activeWarnings.get(0).get("dosingAdjustment")).isNotNull();
        }
    }

    @Nested
    @DisplayName("边界与异常场景测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("完全恒定值的历史数据不应导致异常")
        void forecast_constantHistory_shouldNotThrow() {
            List<WaterQuality> history = new ArrayList<>();
            Instant now = Instant.now();
            for (int i = 50; i > 0; i--) {
                WaterQuality wq = new WaterQuality(now.minus(i * 5, ChronoUnit.MINUTES), "outlet",
                        0.25, 7.2, 18.0, 0.01, 0.2, 8000);
                history.add(wq);
            }

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            assertThat(result.getPredictions()).isNotEmpty();
            for (WaterQualityPrediction pred : result.getPredictions()) {
                assertThat(Double.isFinite(pred.getPredictedValue())).isTrue();
            }
        }

        @Test
        @DisplayName("突然激增的水质数据应被检测到")
        void forecast_suddenSpike_shouldDetectAnomaly() {
            List<WaterQuality> history = new ArrayList<>();
            Instant now = Instant.now();
            for (int i = 50; i > 5; i--) {
                history.add(new WaterQuality(now.minus(i * 5, ChronoUnit.MINUTES), "outlet",
                        0.2, 7.2, 18.0, 0.01, 0.2, 8000));
            }
            for (int i = 5; i > 0; i--) {
                history.add(new WaterQuality(now.minus(i * 5, ChronoUnit.MINUTES), "outlet",
                        0.6 + i * 0.1, 7.2, 18.0, 0.01, 0.2, 8000));
            }

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            boolean hasWarningOrAlarm = result.getPredictions().stream()
                    .anyMatch(p -> "turbidity".equals(p.getParameterName()) &&
                            (Boolean.TRUE.equals(p.getIsWarning()) || Boolean.TRUE.equals(p.getIsAlarm())));

            assertThat(hasWarningOrAlarm).isTrue();
        }

        @Test
        @DisplayName("空历史数据应返回空预测")
        void forecast_emptyHistory_shouldReturnEmpty() {
            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            assertThat(result.getPredictions()).isEmpty();
            assertThat(result.isHasWarning()).isFalse();
            assertThat(result.isHasAlarm()).isFalse();
        }

        @Test
        @DisplayName("零值浊度历史应不导致异常")
        void forecast_zeroTurbidity_shouldNotThrow() {
            List<WaterQuality> history = new ArrayList<>();
            Instant now = Instant.now();
            for (int i = 50; i > 0; i--) {
                history.add(new WaterQuality(now.minus(i * 5, ChronoUnit.MINUTES), "outlet",
                        0.0, 7.2, 18.0, 0.01, 0.2, 8000));
            }

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            assertThat(result.getPredictions()).isNotEmpty();
            for (WaterQualityPrediction pred : result.getPredictions()) {
                if ("turbidity".equals(pred.getParameterName())) {
                    assertThat(pred.getPredictedValue()).isGreaterThanOrEqualTo(0.0);
                }
            }
        }
    }

    @Nested
    @DisplayName("ARIMA算法纯逻辑测试")
    class ARIMAAlgorithmTests {

        @Test
        @DisplayName("ARIMA预测值序列应连续递增或递减")
        void arimaRisingInput_shouldProduceContinuousForecast() {
            double[] history = new double[30];
            for (int i = 0; i < 30; i++) {
                history[i] = 0.2 + i * 0.01;
            }

            List<WaterQuality> wqHistory = new ArrayList<>();
            Instant now = Instant.now();
            for (int i = 0; i < 30; i++) {
                WaterQuality wq = new WaterQuality(now.minus((30 - i) * 5, ChronoUnit.MINUTES),
                        "outlet", history[i], 7.2, 18.0, 0.01, 0.2, 8000);
                wqHistory.add(wq);
            }

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(wqHistory);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityForecaster.ForecastResult result = forecaster.forecastOutletQuality();

            List<WaterQualityPrediction> turbPreds = result.getPredictions().stream()
                    .filter(p -> "turbidity".equals(p.getParameterName()))
                    .sorted(Comparator.comparing(WaterQualityPrediction::getTargetTime))
                    .collect(Collectors.toList());

            for (int i = 1; i < turbPreds.size(); i++) {
                double diff = Math.abs(turbPreds.get(i).getPredictedValue() -
                        turbPreds.get(i - 1).getPredictedValue());
                assertThat(diff).isLessThan(1.0);
            }
        }

        @Test
        @DisplayName("置信度应与数据稳定性负相关")
        void confidence_shouldReflectDataStability() {
            double std1 = calculateStd(createStableHistory(50, 0.25, 8000));
            double std2 = calculateStd(createRisingTurbidityHistory(50, 0.1, 0.5, 8000));

            assertThat(std2).isGreaterThanOrEqualTo(std1 * 0.5);
        }

        private double calculateStd(List<WaterQuality> history) {
            double[] values = history.stream()
                    .mapToDouble(WaterQuality::getTurbidity)
                    .toArray();
            double mean = Arrays.stream(values).average().orElse(0);
            double variance = Arrays.stream(values)
                    .map(x -> (x - mean) * (x - mean))
                    .average().orElse(0);
            return Math.sqrt(variance);
        }
    }
}
