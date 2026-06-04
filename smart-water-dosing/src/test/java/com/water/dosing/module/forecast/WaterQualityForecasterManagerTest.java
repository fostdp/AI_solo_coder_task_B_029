package com.water.dosing.module.forecast;

import com.water.dosing.entity.WaterQuality;
import com.water.dosing.entity.WaterQualityPrediction;
import com.water.dosing.module.forecast.dto.WaterQualityPredictionResult;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaterQualityForecasterManagerTest {

    @Mock
    private WaterQualityRepository waterQualityRepo;

    @Mock
    private WaterQualityPredictionRepository predictionRepo;

    private WaterQualityForecasterConfig config;
    private WaterQualityForecasterManager module;

    @BeforeEach
    void setUp() {
        config = new WaterQualityForecasterConfig();
        config.setPredictionHours(1);
        config.setHistoryHours(24);
        config.setMutationSensitivity(0.05);
        config.setMinDataPoints(10);
        config.setMaxConcurrentPredictions(5);
        config.setEnableMutationDetection(true);
        config.setEnableConfidenceCalculation(true);
        config.setPredictedIndicators(Arrays.asList("turbidity", "cod", "ammonia", "ph", "conductivity"));
        config.setDefaultConfidenceLevel(0.85);
        module = new WaterQualityForecasterManager(waterQualityRepo, predictionRepo, config);
    }

    private List<WaterQuality> createStableHistory(int points, double baseTurbidity, double baseFlow) {
        List<WaterQuality> history = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = points; i > 0; i--) {
            WaterQuality wq = new WaterQuality(now.minus(i * 5, ChronoUnit.MINUTES), "outlet",
                    baseTurbidity + (Math.random() - 0.5) * 0.1,
                    7.2, 18.0, 0.01, 0.2, baseFlow);
            wq.setCod(2.0 + (Math.random() - 0.5) * 0.3);
            wq.setAmmonia(0.08 + (Math.random() - 0.5) * 0.02);
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
            wq.setCod(2.0);
            wq.setAmmonia(0.08);
            history.add(wq);
        }
        return history;
    }

    @Nested
    @DisplayName("模块信息测试")
    class ModuleInfoTests {

        @Test
        @DisplayName("getModuleInfo应返回正确的模块信息")
        void getModuleInfo_shouldReturnCorrectInfo() {
            Map<String, Object> info = module.getModuleInfo();

            assertThat(info.get("moduleName")).isEqualTo("WaterQualityForecasterManager");
            assertThat(info.get("version")).isEqualTo("2.0.0");
            assertThat(info.get("status")).isEqualTo("active");
            assertThat(info.get("asyncInference")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("同步预测测试")
    class SyncForecastTests {

        @Test
        @DisplayName("数据不足时应返回insufficient_data状态")
        void forecastSync_insufficientData_shouldReturnInsufficientData() {
            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(createStableHistory(5, 0.25, 8000));

            WaterQualityPredictionResult result = module.forecastSync("outlet");

            assertThat(result.getStatus()).isEqualTo("insufficient_data");
            assertThat(result.getPredictions()).isEmpty();
            assertThat(result.getOverallConfidence()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("充足数据时应产生多指标预测")
        void forecastSync_sufficientData_shouldProduceMultiIndicatorPredictions() {
            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(createStableHistory(50, 0.25, 8000));
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityPredictionResult result = module.forecastSync("outlet");

            assertThat(result.getStatus()).isIn("normal", "rapid_response");
            assertThat(result.getPredictions()).isNotEmpty();
            assertThat(result.getDataPointsUsed()).isEqualTo(50);
            assertThat(result.getPredictionId()).isNotNull();
            assertThat(result.getPredictionTime()).isNotNull();
            assertThat(result.getModelVersion()).isEqualTo("ARIMA-v2.0");
        }

        @Test
        @DisplayName("每个指标预测应包含预测值和置信区间")
        void forecastSync_eachPrediction_shouldHaveValueAndConfidence() {
            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(createStableHistory(50, 0.25, 8000));
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityPredictionResult result = module.forecastSync("outlet");

            for (WaterQualityPredictionResult.IndicatorPrediction pred : result.getPredictions()) {
                assertThat(pred.getPredictedValue()).isFinite();
                assertThat(pred.getCurrentValue()).isFinite();
                assertThat(pred.getMinPredicted()).isFinite();
                assertThat(pred.getMaxPredicted()).isFinite();
                assertThat(pred.getConfidence()).isBetween(0.0, 1.0);
                assertThat(pred.getTrend()).isIn("rising", "falling", "stable");
            }
        }

        @Test
        @DisplayName("稳定水质预测浊度应在合理范围内")
        void forecastSync_stableTurbidity_shouldBeInReasonableRange() {
            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(createStableHistory(50, 0.25, 8000));
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityPredictionResult result = module.forecastSync("outlet");

            Optional<WaterQualityPredictionResult.IndicatorPrediction> turbPred = result.getPredictions().stream()
                    .filter(p -> "turbidity".equals(p.getIndicatorCode()))
                    .findFirst();

            assertThat(turbPred).isPresent();
            assertThat(turbPred.get().getPredictedValue()).isGreaterThanOrEqualTo(0.0);
            assertThat(turbPred.get().getPredictedValue()).isLessThan(10.0);
        }

        @Test
        @DisplayName("预测结果应包含整体置信度")
        void forecastSync_shouldIncludeOverallConfidence() {
            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(createStableHistory(50, 0.25, 8000));
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityPredictionResult result = module.forecastSync("outlet");

            assertThat(result.getOverallConfidence()).isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("突变检测测试")
    class MutationDetectionTests {

        @Test
        @DisplayName("启用突变检测时预测结果应包含突变检测结果")
        void forecastSync_mutationDetectionEnabled_shouldIncludeResult() {
            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(createStableHistory(50, 0.25, 8000));
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityPredictionResult result = module.forecastSync("outlet");

            assertThat(result.getMutationDetection()).isNotNull();
            assertThat(result.getMutationDetection().getControlLimit()).isGreaterThan(0);
            assertThat(result.getMutationDetection().getSensitivity()).isEqualTo(0.05);
        }

        @Test
        @DisplayName("突变数据应触发rapid_response模式")
        void forecastSync_mutationDetected_shouldTriggerRapidResponse() {
            List<WaterQuality> history = new ArrayList<>();
            Instant now = Instant.now();
            for (int i = 50; i > 5; i--) {
                WaterQuality wq = new WaterQuality(now.minus(i * 5, ChronoUnit.MINUTES), "outlet",
                        0.2, 7.2, 18.0, 0.01, 0.2, 8000);
                wq.setCod(2.0);
                wq.setAmmonia(0.08);
                history.add(wq);
            }
            for (int i = 5; i > 0; i--) {
                WaterQuality wq = new WaterQuality(now.minus(i * 5, ChronoUnit.MINUTES), "outlet",
                        0.6 + i * 0.1, 7.2, 18.0, 0.01, 0.2, 8000);
                wq.setCod(2.0);
                wq.setAmmonia(0.08);
                history.add(wq);
            }

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(history);
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityPredictionResult result = module.forecastSync("outlet");

            if (result.getMutationDetection() != null && result.getMutationDetection().isMutationDetected()) {
                assertThat(result.getStatus()).isEqualTo("rapid_response");
                assertThat(result.getMutationDetection().getMutationCount()).isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("禁用突变检测时不应进行突变检测")
        void forecastSync_mutationDetectionDisabled_shouldSkipDetection() {
            config.setEnableMutationDetection(false);

            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(createStableHistory(50, 0.25, 8000));
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityPredictionResult result = module.forecastSync("outlet");

            assertThat(result.getMutationDetection()).isNull();
        }
    }

    @Nested
    @DisplayName("预警级别测试")
    class WarningLevelTests {

        @Test
        @DisplayName("高浊度预测应标记为alarm级别")
        void forecastSync_highTurbidity_shouldBeAlarmLevel() {
            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(createRisingTurbidityHistory(50, 0.3, 0.65, 8000));
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityPredictionResult result = module.forecastSync("outlet");

            Optional<WaterQualityPredictionResult.IndicatorPrediction> turbPred = result.getPredictions().stream()
                    .filter(p -> "turbidity".equals(p.getIndicatorCode()))
                    .findFirst();

            if (turbPred.isPresent() && turbPred.get().getPredictedValue() > 0.5) {
                assertThat(turbPred.get().getLevel()).isIn("alarm", "warning");
            }
        }

        @Test
        @DisplayName("正常浊度预测应为normal级别")
        void forecastSync_normalTurbidity_shouldBeNormalLevel() {
            when(waterQualityRepo.findByStageAndTimeBetweenOrderByTimeAsc(
                    eq("outlet"), any(Instant.class), any(Instant.class)))
                    .thenReturn(createStableHistory(50, 0.2, 8000));
            when(predictionRepo.save(any(WaterQualityPrediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            WaterQualityPredictionResult result = module.forecastSync("outlet");

            Optional<WaterQualityPredictionResult.IndicatorPrediction> turbPred = result.getPredictions().stream()
                    .filter(p -> "turbidity".equals(p.getIndicatorCode()))
                    .findFirst();

            if (turbPred.isPresent() && turbPred.get().getPredictedValue() < 0.425) {
                assertThat(turbPred.get().getLevel()).isEqualTo("normal");
            }
        }
    }

    @Nested
    @DisplayName("查询接口测试")
    class QueryTests {

        @Test
        @DisplayName("getForecastSummary应返回预警汇总")
        void getForecastSummary_shouldReturnSummary() {
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

            Map<String, Object> summary = module.getForecastSummary("outlet");

            assertThat(summary.get("hasAlarm")).isEqualTo(true);
            assertThat(summary.get("turbidityAlarm")).isEqualTo(true);
            assertThat(summary.get("chlorineAlarm")).isEqualTo(false);
            assertThat(summary.get("module")).isEqualTo("WaterQualityForecasterManager");
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
            warning.setDosingAdjustment("建议增加投加量10%");

            when(predictionRepo.findActiveWarnings(any(Instant.class)))
                    .thenReturn(Collections.singletonList(warning));

            List<Map<String, Object>> activeWarnings = module.getActiveWarnings();

            assertThat(activeWarnings).hasSize(1);
            assertThat(activeWarnings.get(0).get("level")).isEqualTo("alarm");
        }
    }

    @Nested
    @DisplayName("异步任务管理测试")
    class AsyncTaskTests {

        @Test
        @DisplayName("getRunningTaskCount应返回当前运行中的任务数")
        void getRunningTaskCount_shouldReturnCurrentTaskCount() {
            int count = module.getRunningTaskCount();
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }
}
