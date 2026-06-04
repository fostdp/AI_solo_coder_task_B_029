package com.water.dosing.module.membrane.stream;

import com.water.dosing.entity.MembraneModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MembraneStreamProcessorTest {

    private MembraneStreamProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MembraneStreamProcessor();
    }

    private MembraneModule createModule(String code, String name, String type,
                                         double flux, double tmd) {
        MembraneModule m = new MembraneModule(Instant.now(), code, name, type);
        m.setFlux(flux);
        m.setTmd(tmd);
        m.setBaselineFlux("reverse_osmosis".equals(type) ? 25.0 : 60.0);
        m.setBaselineTmd("reverse_osmosis".equals(type) ? 10.0 : 0.8);
        return m;
    }

    @Nested
    @DisplayName("流处理器基本状态测试")
    class BasicStatusTests {

        @Test
        @DisplayName("初始状态应不运行")
        void initialState_shouldNotBeRunning() {
            assertThat(processor.isRunning()).isFalse();
        }

        @Test
        @DisplayName("getStreamStats应返回基本统计信息")
        void getStreamStats_shouldReturnBasicStats() {
            Map<String, Object> stats = processor.getStreamStats();

            assertThat(stats).containsKey("running");
            assertThat(stats).containsKey("processedRecords");
            assertThat(stats).containsKey("detectionEvents");
            assertThat(stats).containsKey("activeModules");
            assertThat(stats.get("running")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("processModuleData实时处理测试")
    class ProcessModuleDataTests {

        @Test
        @DisplayName("处理单个模块数据应产生结果")
        void processModuleData_singleModule_shouldProduceResult() {
            MembraneModule module = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 52.5, 1.05);

            processor.processModuleData(module);

            MembraneStreamProcessor.MembraneStreamResult result = processor.getLatestResult("UF-01");
            assertThat(result).isNotNull();
            assertThat(result.getModuleCode()).isEqualTo("UF-01");
            assertThat(result.getAvgFlux()).isGreaterThan(0);
            assertThat(result.getAvgTmd()).isGreaterThan(0);
            assertThat(result.getWindowSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("连续处理多个数据点应更新滑动平均")
        void processModuleData_multipleDataPoints_shouldUpdateMovingAverage() {
            MembraneModule m1 = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 52.0, 1.05);
            MembraneModule m2 = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 50.0, 1.10);

            processor.processModuleData(m1);
            processor.processModuleData(m2);

            MembraneStreamProcessor.MembraneStreamResult result = processor.getLatestResult("UF-01");
            assertThat(result).isNotNull();
            assertThat(result.getWindowSize()).isEqualTo(2);
            assertThat(result.getAvgFlux()).isBetween(49.0, 53.0);
        }

        @Test
        @DisplayName("通量显著下降应检测到污染趋势")
        void processModuleData_decreasingFlux_shouldDetectFouling() {
            MembraneModule m1 = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 55.0, 0.9);
            MembraneModule m2 = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 25.0, 3.0);

            processor.processModuleData(m1);
            processor.processModuleData(m2);

            MembraneStreamProcessor.MembraneStreamResult result = processor.getLatestResult("UF-01");
            assertThat(result).isNotNull();
            assertThat(result.getFoulingTrend()).isLessThan(1.0);
        }

        @Test
        @DisplayName("严重污染应触发告警")
        void processModuleData_severeFouling_shouldTriggerAlert() {
            MembraneModule m = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 25.0, 3.0);

            processor.processModuleData(m);

            MembraneStreamProcessor.MembraneStreamResult result = processor.getLatestResult("UF-01");
            assertThat(result.isAlertTriggered()).isTrue();
        }

        @Test
        @DisplayName("通量大幅增加且跨膜压差下降应检测到膜更换")
        void processModuleData_fluxIncreaseAndTmdDecrease_shouldDetectReplacement() {
            MembraneModule m1 = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 40.0, 1.5);
            MembraneModule m2 = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 52.0, 1.0);

            processor.processModuleData(m1);
            processor.processModuleData(m2);

            MembraneStreamProcessor.MembraneStreamResult result = processor.getLatestResult("UF-01");
            assertThat(result.isPotentialReplacement()).isTrue();
        }

        @Test
        @DisplayName("不同模块数据应分别处理")
        void processModuleData_differentModules_shouldProcessSeparately() {
            MembraneModule uf = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 52.0, 1.0);
            MembraneModule ro = createModule("RO-01", "反渗透膜组1号", "reverse_osmosis", 21.0, 12.5);

            processor.processModuleData(uf);
            processor.processModuleData(ro);

            MembraneStreamProcessor.MembraneStreamResult ufResult = processor.getLatestResult("UF-01");
            MembraneStreamProcessor.MembraneStreamResult roResult = processor.getLatestResult("RO-01");

            assertThat(ufResult).isNotNull();
            assertThat(roResult).isNotNull();
            assertThat(ufResult.getAvgFlux()).isNotEqualTo(roResult.getAvgFlux());
        }

        @Test
        @DisplayName("未处理的模块应返回null")
        void getLatestResult_unknownModule_shouldReturnNull() {
            MembraneStreamProcessor.MembraneStreamResult result = processor.getLatestResult("UNKNOWN");
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("流处理统计测试")
    class StreamStatsTests {

        @Test
        @DisplayName("处理后应更新统计信息")
        void processModuleData_shouldUpdateStats() {
            MembraneModule m = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 52.0, 1.0);
            processor.processModuleData(m);

            Map<String, Object> stats = processor.getStreamStats();

            assertThat(stats.get("processedRecords")).isEqualTo(1L);
            assertThat(stats.get("activeModules")).isEqualTo(1);
        }

        @Test
        @DisplayName("检测到告警或膜更换时应增加detectionEvents")
        void processModuleData_detectionEvent_shouldIncrementCount() {
            MembraneModule m = createModule("UF-01", "超滤膜组1号", "ultrafiltration", 25.0, 3.0);
            processor.processModuleData(m);

            Map<String, Object> stats = processor.getStreamStats();

            assertThat((Long) stats.get("detectionEvents")).isGreaterThanOrEqualTo(1);
        }
    }
}
