package com.water.dosing.module.watersource;

import com.water.dosing.entity.WaterDistribution;
import com.water.dosing.entity.WaterSource;
import com.water.dosing.module.watersource.dto.OptimizationResultDto;
import com.water.dosing.optimizer.MultiObjectiveWaterOptimizer;
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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaterSourceOptimizerTest {

    @Mock
    private WaterSourceRepository sourceRepo;

    @Mock
    private WaterDistributionRepository distributionRepo;

    @Mock
    private MultiObjectiveWaterOptimizer optimizer;

    private WaterSourceOptimizerConfig config;
    private WaterSourceOptimizer module;

    @BeforeEach
    void setUp() {
        config = new WaterSourceOptimizerConfig();
        config.setTotalDemand(8333.0);
        config.setAutoOptimize(false);
        config.setMaxIterations(100);
        config.setPopulationSize(50);
        config.setSwitchPenaltyWeight(0.3);
        config.setEnforceSwitchDelay(false);
        module = new WaterSourceOptimizer(sourceRepo, distributionRepo, optimizer, config);
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

    @Nested
    @DisplayName("模块信息测试")
    class ModuleInfoTests {

        @Test
        @DisplayName("getModuleInfo应返回正确的模块名称和版本")
        void getModuleInfo_shouldReturnCorrectModuleName() {
            when(sourceRepo.findAllActive()).thenReturn(Collections.emptyList());

            Map<String, Object> info = module.getModuleInfo();

            assertThat(info.get("moduleName")).isEqualTo("WaterSourceOptimizer");
            assertThat(info.get("version")).isEqualTo("1.0.0");
            assertThat(info.get("status")).isEqualTo("active");
        }

        @Test
        @DisplayName("getModuleInfo应返回配置参数")
        void getModuleInfo_shouldReturnConfigParameters() {
            when(sourceRepo.findAllActive()).thenReturn(Collections.emptyList());

            Map<String, Object> info = module.getModuleInfo();

            assertThat(info.get("totalDemand")).isEqualTo(8333.0);
            assertThat(info.get("autoOptimize")).isEqualTo(false);
            assertThat(info.get("switchPenaltyWeight")).isEqualTo(0.3);
        }
    }

    @Nested
    @DisplayName("水源查询测试")
    class SourceQueryTests {

        @Test
        @DisplayName("getAllSources应返回活跃水源")
        void getAllSources_shouldReturnActiveSources() {
            WaterSource s1 = createSource("reservoir", "水库", "surface", 12.0, 7.2, 0.85, 6000);
            WaterSource s2 = createSource("groundwater", "地下水", "ground", 3.5, 7.6, 1.35, 4000);
            when(sourceRepo.findAllActive()).thenReturn(Arrays.asList(s1, s2));

            List<WaterSource> sources = module.getAllSources();

            assertThat(sources).hasSize(2);
            assertThat(sources.get(0).getSourceCode()).isEqualTo("reservoir");
        }

        @Test
        @DisplayName("getLatestSourceData应返回每个水源的最新数据")
        void getLatestSourceData_shouldReturnLatestDataPerSource() {
            WaterSource s1 = createSource("reservoir", "水库", "surface", 12.0, 7.2, 0.85, 6000);
            when(sourceRepo.findAllActive()).thenReturn(Collections.singletonList(s1));
            when(sourceRepo.findLatestBySourceCode("reservoir")).thenReturn(Collections.singletonList(s1));

            Map<String, WaterSource> result = module.getLatestSourceData();

            assertThat(result).containsKey("reservoir");
            assertThat(result.get("reservoir").getTurbidity()).isEqualTo(12.0);
        }

        @Test
        @DisplayName("updateSource应保存到仓库")
        void updateSource_shouldSaveToRepo() {
            WaterSource source = createSource("reservoir", "水库", "surface", 12.0, 7.2, 0.85, 6000);
            when(sourceRepo.save(any(WaterSource.class))).thenReturn(source);

            WaterSource saved = module.updateSource(source);

            assertThat(saved).isNotNull();
            verify(sourceRepo).save(source);
        }
    }

    @Nested
    @DisplayName("优化运行测试")
    class OptimizationTests {

        @Test
        @DisplayName("无活跃水源时应返回不可行结果")
        void runOptimization_noActiveSources_shouldReturnInfeasible() {
            when(sourceRepo.findAllActive()).thenReturn(Collections.emptyList());

            OptimizationResultDto result = module.runOptimization();

            assertThat(result.isFeasible()).isFalse();
            assertThat(result.getSwitchConstraintNote()).isEqualTo("No active water sources");
        }

        @Test
        @DisplayName("优化成功应保存配水结果并返回完整DTO")
        void runOptimization_success_shouldSaveAndReturnDto() {
            WaterSource s1 = createSource("reservoir", "水库", "surface", 12.0, 7.2, 0.85, 6000);
            WaterSource s2 = createSource("groundwater", "地下水", "ground", 3.5, 7.6, 1.35, 4000);

            Map<String, Double> allocations = new LinkedHashMap<>();
            allocations.put("reservoir", 0.65);
            allocations.put("groundwater", 0.35);

            MultiObjectiveWaterOptimizer.OptimizationResult optResult =
                    new MultiObjectiveWaterOptimizer.OptimizationResult(
                            allocations, 7500.0, 9.2, 7.34, -10.5, true);

            when(sourceRepo.findAllActive()).thenReturn(Arrays.asList(s1, s2));
            when(optimizer.optimize(anyList(), eq(8333.0), any())).thenReturn(optResult);
            when(distributionRepo.findCurrentDistribution()).thenReturn(Collections.emptyList());
            when(distributionRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            OptimizationResultDto result = module.runOptimization();

            assertThat(result.isFeasible()).isTrue();
            assertThat(result.getTotalCost()).isEqualTo(7500.0);
            assertThat(result.getMixedTurbidity()).isEqualTo(9.2);
            assertThat(result.getMixedPh()).isEqualTo(7.34);
            assertThat(result.getAllocations()).hasSize(2);
            assertThat(result.getOptimizationId()).isNotNull();
            assertThat(result.getOptimizationTime()).isNotNull();
            verify(distributionRepo).saveAll(anyList());
        }

        @Test
        @DisplayName("新优化应将旧的当前配水标记为非当前")
        void runOptimization_shouldMarkOldDistributionAsNotCurrent() {
            WaterSource s1 = createSource("reservoir", "水库", "surface", 12.0, 7.2, 0.85, 6000);

            Map<String, Double> allocations = new LinkedHashMap<>();
            allocations.put("reservoir", 1.0);

            MultiObjectiveWaterOptimizer.OptimizationResult optResult =
                    new MultiObjectiveWaterOptimizer.OptimizationResult(
                            allocations, 7083.05, 12.0, 7.2, -5.0, true);

            WaterDistribution oldDist = new WaterDistribution(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS),
                    "reservoir", "水库");
            oldDist.setIsCurrent(true);

            when(sourceRepo.findAllActive()).thenReturn(Collections.singletonList(s1));
            when(optimizer.optimize(anyList(), eq(8333.0), any())).thenReturn(optResult);
            when(distributionRepo.findCurrentDistribution()).thenReturn(Collections.singletonList(oldDist));
            when(distributionRepo.save(any(WaterDistribution.class))).thenAnswer(inv -> inv.getArgument(0));
            when(distributionRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            module.runOptimization();

            assertThat(oldDist.getIsCurrent()).isEqualTo(false);
            verify(distributionRepo).save(oldDist);
        }
    }

    @Nested
    @DisplayName("优化状态查询测试")
    class OptimizationStatusTests {

        @Test
        @DisplayName("无配水记录应返回未优化状态")
        void getOptimizationStatus_noDistribution_shouldReturnUnoptimized() {
            when(distributionRepo.findCurrentDistribution()).thenReturn(Collections.emptyList());

            Map<String, Object> result = module.getOptimizationStatus();

            assertThat(result.get("optimized")).isEqualTo(false);
            assertThat(result.get("module")).isEqualTo("WaterSourceOptimizer");
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

            when(distributionRepo.findCurrentDistribution()).thenReturn(Collections.singletonList(dist));

            Map<String, Object> result = module.getOptimizationStatus();

            assertThat(result.get("optimized")).isEqualTo(true);
            assertThat(result.get("optimizationId")).isEqualTo("OPT-123");
            assertThat(result.get("module")).isEqualTo("WaterSourceOptimizer");
        }
    }

    @Nested
    @DisplayName("成本趋势测试")
    class CostTrendTests {

        @Test
        @DisplayName("getDailyCostTrend应按天汇总成本")
        void getDailyCostTrend_shouldAggregateByDay() {
            WaterDistribution d1 = new WaterDistribution(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS),
                    "reservoir", "水库");
            d1.setTotalCost(3500.0);

            when(distributionRepo.findRecentDistributions(any(Instant.class)))
                    .thenReturn(Collections.singletonList(d1));

            List<Map<String, Object>> trend = module.getDailyCostTrend(7);

            assertThat(trend).isNotEmpty();
            assertThat(trend.get(0)).containsKey("date");
            assertThat(trend.get(0)).containsKey("totalCost");
            assertThat(trend.get(0)).containsKey("module");
        }
    }
}
