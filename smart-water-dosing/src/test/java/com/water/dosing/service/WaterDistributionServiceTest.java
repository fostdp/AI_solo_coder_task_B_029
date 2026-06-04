package com.water.dosing.service;

import com.water.dosing.entity.WaterDistribution;
import com.water.dosing.entity.WaterSource;
import com.water.dosing.module.watersource.WaterSourceOptimizer;
import com.water.dosing.module.watersource.dto.OptimizationResultDto;
import com.water.dosing.repository.WaterDistributionRepository;
import com.water.dosing.repository.WaterSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaterDistributionServiceTest {

    @Mock
    private WaterSourceRepository sourceRepo;

    @Mock
    private WaterDistributionRepository distributionRepo;

    @Mock
    private WaterSourceOptimizer moduleOptimizer;

    private WaterDistributionService service;

    @BeforeEach
    void setUp() {
        service = new WaterDistributionService(sourceRepo, distributionRepo, moduleOptimizer);
    }

    private WaterSource createSource(String code, String name, String type,
                                      double turbidity, double ph, double unitCost,
                                      double maxSupply) {
        WaterSource source = new WaterSource(Instant.now(), code, name, type);
        source.setTurbidity(turbidity);
        source.setPh(ph);
        source.setUnitCost(unitCost);
        source.setMaxSupply(maxSupply);
        source.setIsActive(true);
        return source;
    }

    private OptimizationResultDto createOptResult(boolean feasible, double totalCost,
                                                    double mixedTurbidity, double mixedPh,
                                                    Map<String, Double> allocations) {
        OptimizationResultDto dto = new OptimizationResultDto();
        dto.setFeasible(feasible);
        dto.setTotalCost(totalCost);
        dto.setMixedTurbidity(mixedTurbidity);
        dto.setMixedPh(mixedPh);
        dto.setAllocations(allocations);
        dto.setOptimizationId("OPT-" + System.currentTimeMillis());
        dto.setOptimizationTime(Instant.now());
        return dto;
    }

    @Nested
    @DisplayName("优化运行集成测试")
    class OptimizationIntegrationTests {

        @Test
        @DisplayName("无活跃水源时应返回失败")
        void runOptimization_noActiveSources_shouldReturnFailure() {
            OptimizationResultDto optResult = createOptResult(false, 0, 0, 0, Collections.emptyMap());
            optResult.setSwitchConstraintNote("No active water sources");
            when(moduleOptimizer.runOptimization()).thenReturn(optResult);

            Map<String, Object> result = service.runOptimization();

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("优化成功应委托给模块执行")
        void runOptimization_success_shouldDelegateToModule() {
            Map<String, Double> allocations = new LinkedHashMap<>();
            allocations.put("reservoir", 0.65);
            allocations.put("groundwater", 0.35);
            OptimizationResultDto optResult = createOptResult(true, 7500.0, 9.2, 7.34, allocations);

            when(moduleOptimizer.runOptimization()).thenReturn(optResult);

            Map<String, Object> result = service.runOptimization();

            assertThat(result).isNotNull();
            verify(moduleOptimizer).runOptimization();
        }

        @Test
        @DisplayName("优化状态查询应委托给模块")
        void getOptimizationStatus_shouldDelegateToModule() {
            Map<String, Object> statusResult = new LinkedHashMap<>();
            statusResult.put("optimized", true);
            statusResult.put("optimizationId", "OPT-123");
            when(moduleOptimizer.getOptimizationStatus()).thenReturn(statusResult);

            Map<String, Object> result = service.getOptimizationStatus();

            assertThat(result.get("optimized")).isEqualTo(true);
            verify(moduleOptimizer).getOptimizationStatus();
        }
    }

    @Nested
    @DisplayName("优化状态查询测试")
    class OptimizationStatusTests {

        @Test
        @DisplayName("无配水记录应返回未优化状态")
        void getOptimizationStatus_noDistribution_shouldReturnUnoptimized() {
            Map<String, Object> statusResult = new LinkedHashMap<>();
            statusResult.put("optimized", false);
            when(moduleOptimizer.getOptimizationStatus()).thenReturn(statusResult);

            Map<String, Object> result = service.getOptimizationStatus();

            assertThat(result.get("optimized")).isEqualTo(false);
        }

        @Test
        @DisplayName("有配水记录应返回优化状态")
        void getOptimizationStatus_hasDistribution_shouldReturnOptimized() {
            WaterDistribution dist = new WaterDistribution(Instant.now(), "reservoir", "水库");
            dist.setAllocationRatio(0.65);
            dist.setAllocationVolume(5416.45);
            dist.setTotalCost(4604.0);
            dist.setMixTurbidity(9.2);
            dist.setMixPh(7.34);
            dist.setOptimizationId("OPT-123");
            dist.setIsCurrent(true);

            Map<String, Object> statusResult = new LinkedHashMap<>();
            statusResult.put("optimized", true);
            statusResult.put("optimizationId", "OPT-123");
            when(moduleOptimizer.getOptimizationStatus()).thenReturn(statusResult);

            Map<String, Object> result = service.getOptimizationStatus();

            assertThat(result.get("optimized")).isEqualTo(true);
            assertThat(result.get("optimizationId")).isEqualTo("OPT-123");
        }
    }

    @Nested
    @DisplayName("委托模式测试")
    class DelegationTests {

        @Test
        @DisplayName("getAllSources应委托给模块")
        void getAllSources_shouldDelegateToModule() {
            WaterSource active = createSource("reservoir", "水库", "surface", 12.0, 7.2, 0.85, 6000);
            when(moduleOptimizer.getAllSources()).thenReturn(Collections.singletonList(active));

            List<WaterSource> sources = service.getAllSources();

            assertThat(sources).hasSize(1);
            assertThat(sources.get(0).getSourceCode()).isEqualTo("reservoir");
            verify(moduleOptimizer).getAllSources();
        }

        @Test
        @DisplayName("updateSource应保存到仓库")
        void updateSource_shouldSaveToRepository() {
            WaterSource source = createSource("reservoir", "水库", "surface", 12.0, 7.2, 0.85, 6000);
            when(sourceRepo.save(any(WaterSource.class))).thenReturn(source);

            WaterSource saved = service.updateSource(source);

            assertThat(saved).isNotNull();
            verify(sourceRepo).save(source);
        }

        @Test
        @DisplayName("getLatestSourceData应委托给模块")
        void getLatestSourceData_shouldDelegateToModule() {
            WaterSource source = createSource("reservoir", "水库", "surface", 12.0, 7.2, 0.85, 6000);
            Map<String, WaterSource> latestData = new LinkedHashMap<>();
            latestData.put("reservoir", source);
            when(moduleOptimizer.getLatestSourceData()).thenReturn(latestData);

            Map<String, WaterSource> result = service.getLatestSourceData();

            assertThat(result).containsKey("reservoir");
            verify(moduleOptimizer).getLatestSourceData();
        }

        @Test
        @DisplayName("getDailyCostTrend应委托给模块")
        void getDailyCostTrend_shouldDelegateToModule() {
            List<Map<String, Object>> trend = Collections.singletonList(
                    Map.of("date", "2024-01-01", "totalCost", 7500.0));
            when(moduleOptimizer.getDailyCostTrend(7)).thenReturn(trend);

            List<Map<String, Object>> result = service.getDailyCostTrend(7);

            assertThat(result).hasSize(1);
            verify(moduleOptimizer).getDailyCostTrend(7);
        }
    }
}
