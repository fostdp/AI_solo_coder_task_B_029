package com.water.dosing.module.membrane;

import com.water.dosing.entity.MembraneCleanRecord;
import com.water.dosing.entity.MembraneModule;
import com.water.dosing.module.membrane.dto.FoulingEvaluationResult;
import com.water.dosing.module.membrane.stream.MembraneStreamProcessor;
import com.water.dosing.repository.MembraneCleanRecordRepository;
import com.water.dosing.repository.MembraneModuleRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembraneMonitorTest {

    @Mock
    private MembraneModuleRepository moduleRepo;

    @Mock
    private MembraneCleanRecordRepository cleanRecordRepo;

    @Mock
    private MembraneStreamProcessor streamProcessor;

    private MembraneMonitorConfig config;
    private MembraneMonitor module;

    @BeforeEach
    void setUp() {
        config = new MembraneMonitorConfig();
        config.setUfBaseFlux(60.0);
        config.setRoBaseFlux(25.0);
        config.setUfBaseTmd(0.8);
        config.setRoBaseTmd(10.0);
        config.setFoulingWarningThreshold(0.75);
        config.setFoulingAlarmThreshold(0.5);
        config.setStreamProcessingEnabled(false);
        module = new MembraneMonitor(moduleRepo, cleanRecordRepo, config, streamProcessor);
    }

    private MembraneModule createModule(String code, String name, String type,
                                         double flux, double tmd, double foulingIndex,
                                         Integer foulingLevel, String urgency,
                                         Instant lastCleanTime, Integer predictedDays) {
        MembraneModule m = new MembraneModule(Instant.now(), code, name, type);
        m.setFlux(flux);
        m.setTmd(tmd);
        m.setFoulingIndex(foulingIndex);
        m.setFoulingLevel(foulingLevel);
        m.setCleanUrgency(urgency);
        m.setLastCleanTime(lastCleanTime);
        m.setPredictedCleanDays(predictedDays);
        m.setIsActive(true);
        return m;
    }

    @Nested
    @DisplayName("模块信息测试")
    class ModuleInfoTests {

        @Test
        @DisplayName("getModuleInfo应返回正确的模块名称和版本")
        void getModuleInfo_shouldReturnCorrectInfo() {
            when(moduleRepo.findAllActive()).thenReturn(Collections.emptyList());
            when(streamProcessor.isRunning()).thenReturn(false);
            when(streamProcessor.getStreamStats()).thenReturn(Collections.emptyMap());

            Map<String, Object> info = module.getModuleInfo();

            assertThat(info.get("moduleName")).isEqualTo("MembraneMonitor");
            assertThat(info.get("version")).isEqualTo("1.0.0");
            assertThat(info.get("status")).isEqualTo("active");
        }
    }

    @Nested
    @DisplayName("膜污染评估测试")
    class FoulingEvaluationTests {

        @Test
        @DisplayName("正常UF膜应标记为normal")
        void evaluateFouling_normalUFModule_shouldBeNormal() {
            String code = "UF-01";
            MembraneModule latest = createModule(code, "正常超滤膜", "ultrafiltration",
                    52.5, 1.05, 0.82, 2, "soon",
                    Instant.now().minus(15, ChronoUnit.DAYS), 5);
            latest.setFoulingIndex(0.82);

            when(moduleRepo.findAllActiveModuleCodes()).thenReturn(Collections.singletonList(code));
            when(moduleRepo.findByModuleCodeOrderByTimeDesc(code)).thenReturn(Collections.singletonList(latest));
            when(moduleRepo.save(any(MembraneModule.class))).thenAnswer(inv -> inv.getArgument(0));

            FoulingEvaluationResult result = module.evaluateFoulingAndPredictCleaning();

            assertThat(result.getTotalModules()).isEqualTo(1);
            assertThat(result.getModuleStatuses()).hasSize(1);
            assertThat(result.getModuleStatuses().get(0).getModuleCode()).isEqualTo(code);
        }

        @Test
        @DisplayName("严重污染UF膜应标记为urgent")
        void evaluateFouling_severelyFouledModule_shouldBeUrgent() {
            String code = "UF-BAD";
            MembraneModule latest = createModule(code, "污染超滤膜", "ultrafiltration",
                    25.0, 2.5, 0.3, 3, "urgent",
                    Instant.now().minus(60, ChronoUnit.DAYS), 1);
            latest.setFoulingIndex(0.3);

            when(moduleRepo.findAllActiveModuleCodes()).thenReturn(Collections.singletonList(code));
            when(moduleRepo.findByModuleCodeOrderByTimeDesc(code)).thenReturn(Collections.singletonList(latest));
            when(moduleRepo.save(any(MembraneModule.class))).thenAnswer(inv -> inv.getArgument(0));

            FoulingEvaluationResult result = module.evaluateFoulingAndPredictCleaning();

            assertThat(result.getUrgentCount()).isGreaterThanOrEqualTo(1);
            assertThat(result.getModuleStatuses().get(0).getCleanUrgency()).isEqualTo("urgent");
        }

        @Test
        @DisplayName("RO膜应使用RO基准通量")
        void evaluateFouling_roModule_shouldUseROBaseFlux() {
            String code = "RO-01";
            MembraneModule latest = createModule(code, "RO膜组", "reverse_osmosis",
                    21.0, 12.0, 0.85, 1, "normal",
                    Instant.now().minus(10, ChronoUnit.DAYS), 20);
            latest.setFoulingIndex(0.85);

            when(moduleRepo.findAllActiveModuleCodes()).thenReturn(Collections.singletonList(code));
            when(moduleRepo.findByModuleCodeOrderByTimeDesc(code)).thenReturn(Collections.singletonList(latest));
            when(moduleRepo.save(any(MembraneModule.class))).thenAnswer(inv -> inv.getArgument(0));

            FoulingEvaluationResult result = module.evaluateFoulingAndPredictCleaning();

            FoulingEvaluationResult.ModuleFoulingStatus status = result.getModuleStatuses().get(0);
            double normalizedFlux = latest.getFlux() / config.getRoBaseFlux();
            assertThat(status.getBaselineFlux()).isCloseTo(config.getRoBaseFlux(), within(0.01));
        }

        @Test
        @DisplayName("无历史数据时应返回错误信息")
        void evaluateFouling_noHistoryData_shouldReturnError() {
            String code = "UF-EMPTY";
            when(moduleRepo.findAllActiveModuleCodes()).thenReturn(Collections.singletonList(code));
            when(moduleRepo.findByModuleCodeOrderByTimeDesc(code)).thenReturn(Collections.emptyList());

            FoulingEvaluationResult result = module.evaluateFoulingAndPredictCleaning();

            assertThat(result.getModuleStatuses().get(0).getNotes()).isEqualTo("No data available");
        }

        @Test
        @DisplayName("多模块评估应正确统计各状态数量")
        void evaluateFouling_multipleModules_shouldCountCorrectly() {
            List<String> codes = Arrays.asList("UF-01", "UF-02", "RO-01");

            MembraneModule uf1 = createModule("UF-01", "超滤1", "ultrafiltration",
                    25.0, 3.0, 0.25, 3, "urgent",
                    Instant.now().minus(60, ChronoUnit.DAYS), 1);
            uf1.setFoulingIndex(0.25);

            MembraneModule uf2 = createModule("UF-02", "超滤2", "ultrafiltration",
                    42.0, 1.5, 0.6, 2, "soon",
                    Instant.now().minus(35, ChronoUnit.DAYS), 5);
            uf2.setFoulingIndex(0.6);

            MembraneModule ro1 = createModule("RO-01", "RO1", "reverse_osmosis",
                    22.0, 11.0, 0.88, 1, "normal",
                    Instant.now().minus(10, ChronoUnit.DAYS), 25);
            ro1.setFoulingIndex(0.88);

            when(moduleRepo.findAllActiveModuleCodes()).thenReturn(codes);
            when(moduleRepo.findByModuleCodeOrderByTimeDesc("UF-01")).thenReturn(Collections.singletonList(uf1));
            when(moduleRepo.findByModuleCodeOrderByTimeDesc("UF-02")).thenReturn(Collections.singletonList(uf2));
            when(moduleRepo.findByModuleCodeOrderByTimeDesc("RO-01")).thenReturn(Collections.singletonList(ro1));
            when(moduleRepo.save(any(MembraneModule.class))).thenAnswer(inv -> inv.getArgument(0));

            FoulingEvaluationResult result = module.evaluateFoulingAndPredictCleaning();

            assertThat(result.getUrgentCount()).isEqualTo(1);
            assertThat(result.getTotalModules()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("清洗记录测试")
    class CleaningRecordTests {

        @Test
        @DisplayName("记录清洗后膜组应重置为正常状态")
        void recordCleaning_shouldResetModuleToNormal() {
            MembraneCleanRecord record = new MembraneCleanRecord();
            record.setModuleCode("UF-01");
            record.setCleanTime(Instant.now());
            record.setCleanType("chemical");
            record.setFluxBefore(40.0);
            record.setFluxAfter(58.0);

            MembraneModule mod = createModule("UF-01", "超滤膜组1号", "ultrafiltration",
                    40.0, 1.5, 0.6, 2, "soon",
                    Instant.now().minus(30, ChronoUnit.DAYS), 5);

            when(cleanRecordRepo.save(any(MembraneCleanRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(moduleRepo.findLatestByModuleCode("UF-01")).thenReturn(Collections.singletonList(mod));
            when(moduleRepo.save(any(MembraneModule.class))).thenAnswer(inv -> inv.getArgument(0));

            MembraneCleanRecord saved = module.recordCleaning(record);

            assertThat(saved).isNotNull();
            verify(moduleRepo).save(argThat(m ->
                    m.getCleanUrgency().equals("normal") &&
                    m.getFoulingIndex() == 0.95 &&
                    m.getFoulingLevel() == 1 &&
                    m.getPredictedCleanDays() == 30
            ));
        }

        @Test
        @DisplayName("清洗恢复率应在合理范围")
        void recordCleaning_fluxRecovery_shouldBeWithinRange() {
            MembraneCleanRecord record = new MembraneCleanRecord();
            record.setModuleCode("UF-01");
            record.setCleanTime(Instant.now());
            record.setCleanType("chemical");
            record.setFluxBefore(40.0);
            record.setFluxAfter(58.0);

            MembraneModule mod = createModule("UF-01", "超滤膜组1号", "ultrafiltration",
                    40.0, 1.5, 0.6, 2, "soon",
                    Instant.now().minus(30, ChronoUnit.DAYS), 5);

            when(cleanRecordRepo.save(any(MembraneCleanRecord.class))).thenAnswer(inv -> {
                MembraneCleanRecord r = inv.getArgument(0);
                assertThat(r.getFluxRecoveryPct()).isBetween(0.0, 100.0);
                return r;
            });
            when(moduleRepo.findLatestByModuleCode("UF-01")).thenReturn(Collections.singletonList(mod));
            when(moduleRepo.save(any(MembraneModule.class))).thenAnswer(inv -> inv.getArgument(0));

            module.recordCleaning(record);
        }
    }

    @Nested
    @DisplayName("膜组状态和清洗计划测试")
    class ModuleStatusTests {

        @Test
        @DisplayName("getAllModulesStatus应正确分类urgent和soon模块")
        void getAllModulesStatus_shouldClassifyCorrectly() {
            MembraneModule urgent = createModule("RO-02", "紧急RO膜", "reverse_osmosis",
                    18.5, 15.8, 0.48, 3, "urgent",
                    Instant.now().minus(55, ChronoUnit.DAYS), 1);
            MembraneModule soon = createModule("UF-01", "即将超滤膜", "ultrafiltration",
                    42.0, 1.5, 0.65, 2, "soon",
                    Instant.now().minus(35, ChronoUnit.DAYS), 5);
            MembraneModule normal = createModule("UF-02", "正常超滤膜", "ultrafiltration",
                    58.2, 0.88, 0.93, 1, "normal",
                    Instant.now().minus(10, ChronoUnit.DAYS), 20);

            when(moduleRepo.findAllActive()).thenReturn(Arrays.asList(urgent, soon, normal));
            when(streamProcessor.getLatestResult(anyString())).thenReturn(null);

            Map<String, Object> result = module.getAllModulesStatus();

            List<String> urgentCodes = (List<String>) result.get("urgentModules");
            List<String> soonCodes = (List<String>) result.get("soonModules");
            assertThat(urgentCodes).containsExactly("RO-02");
            assertThat(soonCodes).containsExactly("UF-01");
            assertThat(result.get("needAttentionCount")).isEqualTo(2);
        }

        @Test
        @DisplayName("getCleaningSchedule应按紧急程度排序")
        void getCleaningSchedule_shouldSortByUrgency() {
            MembraneModule soon = createModule("UF-01", "即将超滤膜", "ultrafiltration",
                    42.0, 1.5, 0.65, 2, "soon",
                    Instant.now().minus(35, ChronoUnit.DAYS), 5);
            MembraneModule urgent = createModule("RO-02", "紧急RO膜", "reverse_osmosis",
                    18.5, 15.8, 0.48, 3, "urgent",
                    Instant.now().minus(55, ChronoUnit.DAYS), 1);

            when(moduleRepo.findModulesNeedingCleaning(Arrays.asList("urgent", "soon")))
                    .thenReturn(Arrays.asList(soon, urgent));

            List<Map<String, Object>> schedule = module.getCleaningSchedule();

            assertThat(schedule).hasSize(2);
            assertThat(schedule.get(0).get("cleanUrgency")).isEqualTo("urgent");
            assertThat(schedule.get(1).get("cleanUrgency")).isEqualTo("soon");
        }
    }

    @Nested
    @DisplayName("膜更换测试")
    class ReplacementTests {

        @Test
        @DisplayName("手动记录膜更换应重置基线")
        void recordMembraneReplacement_shouldResetBaseline() {
            MembraneModule mod = createModule("UF-01", "超滤膜组1号", "ultrafiltration",
                    40.0, 1.5, 0.6, 2, "soon",
                    Instant.now().minus(30, ChronoUnit.DAYS), 5);

            when(moduleRepo.findLatestByModuleCode("UF-01")).thenReturn(Collections.singletonList(mod));
            when(moduleRepo.save(any(MembraneModule.class))).thenAnswer(inv -> inv.getArgument(0));
            when(streamProcessor.isRunning()).thenReturn(false);

            Map<String, Object> result = module.recordMembraneReplacement("UF-01", "SN-NEW-001", "admin");

            assertThat(result.get("success")).isEqualTo(true);
            verify(moduleRepo).save(argThat(m ->
                    m.getIsReplacedRecently().equals(true) &&
                    m.getFoulingIndex() == 0.98 &&
                    m.getCleanUrgency().equals("normal")
            ));
        }

        @Test
        @DisplayName("不存在的模块应返回失败")
        void recordMembraneReplacement_notFound_shouldReturnFailure() {
            when(moduleRepo.findLatestByModuleCode("UF-99")).thenReturn(Collections.emptyList());

            Map<String, Object> result = module.recordMembraneReplacement("UF-99", "SN-001", "admin");

            assertThat(result.get("success")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("流处理控制测试")
    class StreamControlTests {

        @Test
        @DisplayName("未运行时startStreamProcessing应启动")
        void startStreamProcessing_shouldStartWhenNotRunning() {
            when(streamProcessor.isRunning()).thenReturn(false);
            when(moduleRepo.findAllActive()).thenReturn(Collections.emptyList());

            boolean started = module.startStreamProcessing();

            verify(streamProcessor).startStreamProcessing(anyList(), eq(config.getStreamParallelism()));
        }

        @Test
        @DisplayName("运行中时startStreamProcessing不应重复启动")
        void startStreamProcessing_alreadyRunning_shouldNotRestart() {
            when(streamProcessor.isRunning()).thenReturn(true);

            boolean started = module.startStreamProcessing();

            assertThat(started).isFalse();
            verify(streamProcessor, never()).startStreamProcessing(anyList(), anyInt());
        }

        @Test
        @DisplayName("stopStreamProcessing应停止流处理")
        void stopStreamProcessing_shouldStop() {
            when(streamProcessor.isRunning()).thenReturn(true, false);

            boolean stopped = module.stopStreamProcessing();

            verify(streamProcessor).stop();
        }
    }
}
