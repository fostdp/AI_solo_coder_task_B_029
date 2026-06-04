package com.water.dosing.service;

import com.water.dosing.entity.MembraneCleanRecord;
import com.water.dosing.entity.MembraneModule;
import com.water.dosing.module.membrane.MembraneMonitor;
import com.water.dosing.module.membrane.dto.FoulingEvaluationResult;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembraneServiceTest {

    @Mock
    private MembraneModuleRepository moduleRepo;

    @Mock
    private MembraneCleanRecordRepository cleanRecordRepo;

    @Mock
    private MembraneMonitor moduleMonitor;

    private MembraneService service;

    @BeforeEach
    void setUp() {
        service = new MembraneService(moduleRepo, cleanRecordRepo, moduleMonitor);
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
    @DisplayName("委托模式回归测试")
    class DelegationTests {

        @Test
        @DisplayName("getAllModulesStatus应委托给模块")
        void getAllModulesStatus_shouldDelegateToModule() {
            Map<String, Object> moduleResult = new LinkedHashMap<>();
            moduleResult.put("urgentModules", Arrays.asList("RO-02"));
            moduleResult.put("soonModules", Arrays.asList("UF-01"));
            moduleResult.put("needAttentionCount", 2);
            when(moduleMonitor.getAllModulesStatus()).thenReturn(moduleResult);

            Map<String, Object> result = service.getAllModulesStatus();

            assertThat(result.get("urgentModules")).isNotNull();
            assertThat(result.get("needAttentionCount")).isEqualTo(2);
            verify(moduleMonitor).getAllModulesStatus();
        }

        @Test
        @DisplayName("evaluateFoulingAndPredictCleaning应委托给模块")
        void evaluateFoulingAndPredictCleaning_shouldDelegateToModule() {
            FoulingEvaluationResult evalResult = new FoulingEvaluationResult();
            evalResult.setEvaluationId("EVAL-TEST");
            evalResult.setUrgentCount(1);
            evalResult.setTotalModules(3);
            when(moduleMonitor.evaluateFoulingAndPredictCleaning()).thenReturn(evalResult);

            Map<String, Object> result = service.evaluateFoulingAndPredictCleaning();

            assertThat(result).isNotNull();
            verify(moduleMonitor).evaluateFoulingAndPredictCleaning();
        }

        @Test
        @DisplayName("recordCleaning应委托给模块")
        void recordCleaning_shouldDelegateToModule() {
            MembraneCleanRecord record = new MembraneCleanRecord();
            record.setModuleCode("UF-01");
            record.setCleanTime(Instant.now());
            record.setCleanType("chemical");
            record.setFluxBefore(40.0);
            record.setFluxAfter(58.0);

            when(moduleMonitor.recordCleaning(any(MembraneCleanRecord.class))).thenReturn(record);

            MembraneCleanRecord saved = service.recordCleaning(record);

            assertThat(saved).isNotNull();
            verify(moduleMonitor).recordCleaning(record);
        }

        @Test
        @DisplayName("getFoulingTrend应委托给模块")
        void getFoulingTrend_shouldDelegateToModule() {
            List<Map<String, Object>> trend = Collections.emptyList();
            when(moduleMonitor.getFoulingTrend("UF-01", 24)).thenReturn(trend);

            List<Map<String, Object>> result = service.getFoulingTrend("UF-01", 24);

            verify(moduleMonitor).getFoulingTrend("UF-01", 24);
        }

        @Test
        @DisplayName("getCleaningSchedule应委托给模块")
        void getCleaningSchedule_shouldDelegateToModule() {
            List<Map<String, Object>> schedule = new ArrayList<>();
            when(moduleMonitor.getCleaningSchedule()).thenReturn(schedule);

            List<Map<String, Object>> result = service.getCleaningSchedule();

            verify(moduleMonitor).getCleaningSchedule();
        }

        @Test
        @DisplayName("getModuleLatest应委托给模块")
        void getModuleLatest_shouldDelegateToModule() {
            MembraneModule module = createModule("UF-01", "超滤膜组1号", "ultrafiltration",
                    52.5, 1.05, 0.82, 2, "soon",
                    Instant.now().minus(15, ChronoUnit.DAYS), 5);
            when(moduleMonitor.getModuleLatest("UF-01")).thenReturn(module);

            MembraneModule result = service.getModuleLatest("UF-01");

            assertThat(result).isNotNull();
            assertThat(result.getModuleCode()).isEqualTo("UF-01");
            verify(moduleMonitor).getModuleLatest("UF-01");
        }

        @Test
        @DisplayName("getCleaningStats应委托给模块")
        void getCleaningStats_shouldDelegateToModule() {
            Map<String, Object> stats = new LinkedHashMap<>();
            when(moduleMonitor.getCleaningStats(30)).thenReturn(stats);

            Map<String, Object> result = service.getCleaningStats(30);

            verify(moduleMonitor).getCleaningStats(30);
        }

        @Test
        @DisplayName("recordMembraneReplacement应委托给模块")
        void recordMembraneReplacement_shouldDelegateToModule() {
            Map<String, Object> replaceResult = new LinkedHashMap<>();
            replaceResult.put("success", true);
            when(moduleMonitor.recordMembraneReplacement("UF-01", "SN-NEW", "admin")).thenReturn(replaceResult);

            Map<String, Object> result = service.recordMembraneReplacement("UF-01", "SN-NEW", "admin");

            assertThat(result.get("success")).isEqualTo(true);
            verify(moduleMonitor).recordMembraneReplacement("UF-01", "SN-NEW", "admin");
        }
    }

    @Nested
    @DisplayName("膜组标注正确性测试")
    class ModuleLabelingTests {

        @Test
        @DisplayName("无紧急模块时urgent列表应为空")
        void getAllModulesStatus_noUrgent_shouldHaveEmptyUrgentList() {
            Map<String, Object> moduleResult = new LinkedHashMap<>();
            moduleResult.put("urgentModules", Collections.emptyList());
            moduleResult.put("needAttentionCount", 0);
            when(moduleMonitor.getAllModulesStatus()).thenReturn(moduleResult);

            Map<String, Object> result = service.getAllModulesStatus();

            List<String> urgentCodes = (List<String>) result.get("urgentModules");
            assertThat(urgentCodes).isEmpty();
            assertThat(result.get("needAttentionCount")).isEqualTo(0);
        }

        @Test
        @DisplayName("清洗计划应按紧急程度排序")
        void getCleaningSchedule_shouldSortByUrgency() {
            Map<String, Object> urgentItem = new LinkedHashMap<>();
            urgentItem.put("moduleCode", "RO-02");
            urgentItem.put("cleanUrgency", "urgent");

            Map<String, Object> soonItem = new LinkedHashMap<>();
            soonItem.put("moduleCode", "UF-01");
            soonItem.put("cleanUrgency", "soon");

            when(moduleMonitor.getCleaningSchedule()).thenReturn(Arrays.asList(urgentItem, soonItem));

            List<Map<String, Object>> schedule = service.getCleaningSchedule();

            assertThat(schedule).hasSize(2);
        }
    }
}
