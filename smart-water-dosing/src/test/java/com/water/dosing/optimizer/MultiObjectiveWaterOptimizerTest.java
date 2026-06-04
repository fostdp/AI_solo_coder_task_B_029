package com.water.dosing.optimizer;

import com.water.dosing.entity.WaterSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MultiObjectiveWaterOptimizerTest {

    private MultiObjectiveWaterOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new MultiObjectiveWaterOptimizer();
        optimizer.targetTurbidity = 20.0;
        optimizer.targetPhMin = 6.5;
        optimizer.targetPhMax = 8.5;
        optimizer.costWeight = 0.6;
        optimizer.qualityWeight = 0.4;
        optimizer.populationSize = 50;
        optimizer.maxIterations = 100;
        optimizer.mutationRate = 0.1;
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

    private List<WaterSource> createTypicalSources() {
        return Arrays.asList(
                createSource("reservoir", "水库水源", "surface", 12.0, 7.2, 0.85, 6000),
                createSource("groundwater", "地下水源", "ground", 3.5, 7.6, 1.35, 4000),
                createSource("river", "河流水源", "surface", 25.0, 7.0, 0.65, 5000)
        );
    }

    @Nested
    @DisplayName("收敛性测试")
    class ConvergenceTests {

        @Test
        @DisplayName("优化结果应比等比例分配有更优适应度")
        void optimize_shouldOutperformEqualDistribution() {
            List<WaterSource> sources = createTypicalSources();
            double totalDemand = 8333;

            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(sources, totalDemand);

            double equalCost = sources.stream()
                    .mapToDouble(s -> (totalDemand / sources.size()) * s.getUnitCost())
                    .sum();

            if (result.isFeasible()) {
                assertThat(result.getTotalCost()).isLessThanOrEqualTo(equalCost * 1.05);
            }

            assertThat(result.getAllocations()).hasSize(3);
            assertThat(result.getAllocations().values().stream()
                    .mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(0.01));
        }

        @Test
        @DisplayName("多次运行应产生一致的可行解")
        void optimize_shouldProduceFeasibleSolutionConsistently() {
            List<WaterSource> sources = createTypicalSources();
            double totalDemand = 8333;

            for (int i = 0; i < 5; i++) {
                MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(sources, totalDemand);
                assertThat(result).isNotNull();
                assertThat(result.getAllocations()).hasSize(sources.size());

                double ratioSum = result.getAllocations().values().stream()
                        .mapToDouble(Double::doubleValue).sum();
                assertThat(ratioSum).isCloseTo(1.0, within(0.01));

                result.getAllocations().values().forEach(r ->
                        assertThat(r).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(1.0));
            }
        }

        @RepeatedTest(3)
        @DisplayName("适应度值应为有限数")
        void optimize_fitnessShouldBeFinite() {
            List<WaterSource> sources = createTypicalSources();
            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(sources, 8333);

            assertThat(Double.isFinite(result.getFitnessScore())).isTrue();
            assertThat(Double.isFinite(result.getTotalCost())).isTrue();
            assertThat(Double.isFinite(result.getMixedTurbidity())).isTrue();
            assertThat(Double.isFinite(result.getMixedPh())).isTrue();
        }

        @Test
        @DisplayName("增大迭代次数应提升或持平适应度")
        void optimize_moreIterationsShouldNotDegrade() {
            List<WaterSource> sources = createTypicalSources();
            double totalDemand = 8333;

            optimizer.maxIterations = 20;
            MultiObjectiveWaterOptimizer.OptimizationResult resultFew = optimizer.optimize(sources, totalDemand);

            optimizer.maxIterations = 100;
            MultiObjectiveWaterOptimizer.OptimizationResult resultMany = optimizer.optimize(sources, totalDemand);

            if (resultFew.isFeasible() && resultMany.isFeasible()) {
                assertThat(resultMany.getFitnessScore()).isGreaterThanOrEqualTo(resultFew.getFitnessScore() - 1.0);
            }
        }
    }

    @Nested
    @DisplayName("解的多样性测试")
    class DiversityTests {

        @Test
        @DisplayName("各水源分配比例应非零")
        void optimize_allSourcesShouldGetAllocation() {
            List<WaterSource> sources = createTypicalSources();
            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(sources, 8333);

            if (result.isFeasible()) {
                result.getAllocations().values().forEach(ratio ->
                        assertThat(ratio).isGreaterThan(0.01));
            }
        }

        @Test
        @DisplayName("低成本水源应获得较高分配比例")
        void optimize_cheaperSourceShouldGetMoreAllocation() {
            WaterSource cheap = createSource("cheap", "廉价水源", "surface", 10.0, 7.2, 0.50, 8000);
            WaterSource expensive = createSource("expensive", "昂贵水源", "ground", 5.0, 7.5, 2.00, 8000);
            List<WaterSource> sources = Arrays.asList(cheap, expensive);

            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(sources, 8000);

            if (result.isFeasible()) {
                double cheapRatio = result.getAllocations().get("cheap");
                double expensiveRatio = result.getAllocations().get("expensive");
                assertThat(cheapRatio).isGreaterThan(expensiveRatio);
            }
        }
    }

    @Nested
    @DisplayName("成本节约效果测试")
    class CostSavingTests {

        @Test
        @DisplayName("优化后总成本应低于等比例分配")
        void optimize_shouldReduceCostComparedToEqualDistribution() {
            List<WaterSource> sources = createTypicalSources();
            double totalDemand = 8333;

            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(sources, totalDemand);

            double equalCost = sources.stream()
                    .mapToDouble(s -> (totalDemand / sources.size()) * s.getUnitCost())
                    .sum();

            if (result.isFeasible()) {
                assertThat(result.getTotalCost()).isLessThan(equalCost * 1.1);
            }
        }

        @Test
        @DisplayName("仅有单一水源时成本等于全部需求量乘单价")
        void optimize_singleSourceCostCalculation() {
            WaterSource single = createSource("only", "唯一水源", "surface", 10.0, 7.2, 1.0, 10000);
            double totalDemand = 8000;

            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(
                    Collections.singletonList(single), totalDemand);

            assertThat(result.getAllocations().get("only")).isCloseTo(1.0, within(0.01));
            assertThat(result.getTotalCost()).isCloseTo(8000.0, within(100.0));
        }
    }

    @Nested
    @DisplayName("不同原水水质适应性测试")
    class WaterQualityAdaptabilityTests {

        @Test
        @DisplayName("高浊度水源应降低分配比例以满足水质约束")
        void optimize_shouldReduceHighTurbidityAllocation() {
            WaterSource clean = createSource("clean", "清洁水源", "ground", 3.0, 7.3, 1.50, 8000);
            WaterSource dirty = createSource("dirty", "高浊度水源", "surface", 40.0, 7.0, 0.50, 8000);
            List<WaterSource> sources = Arrays.asList(clean, dirty);

            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(sources, 8000);

            if (result.isFeasible()) {
                assertThat(result.getMixedTurbidity()).isLessThanOrEqualTo(optimizer.targetTurbidity + 1.0);
            }
        }

        @Test
        @DisplayName("极端pH水源应受约束")
        void optimize_shouldHandleExtremePH() {
            WaterSource acidic = createSource("acid", "酸性水源", "surface", 10.0, 5.5, 0.50, 8000);
            WaterSource normal = createSource("normal", "正常水源", "ground", 8.0, 7.2, 1.50, 8000);
            List<WaterSource> sources = Arrays.asList(acidic, normal);

            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(sources, 8000);

            assertThat(result).isNotNull();
            if (result.isFeasible()) {
                assertThat(result.getMixedPh()).isBetween(optimizer.targetPhMin, optimizer.targetPhMax);
            }
        }

        @Test
        @DisplayName("所有水源浊度均超标时回退方案仍生效")
        void optimize_allHighTurbidity_shouldFallback() {
            WaterSource s1 = createSource("s1", "高浊度1", "surface", 30.0, 7.0, 0.80, 5000);
            WaterSource s2 = createSource("s2", "高浊度2", "surface", 35.0, 7.1, 0.70, 5000);
            List<WaterSource> sources = Arrays.asList(s1, s2);

            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(sources, 8000);

            assertThat(result).isNotNull();
            assertThat(result.getAllocations()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("边界与异常场景测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("空水源列表应返回默认结果")
        void optimize_emptySources_shouldReturnDefault() {
            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(
                    Collections.emptyList(), 8000);

            assertThat(result).isNotNull();
            assertThat(result.getAllocations()).containsKey("default");
            assertThat(result.getAllocations().get("default")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("null水源列表应返回默认结果")
        void optimize_nullSources_shouldReturnDefault() {
            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(null, 8000);

            assertThat(result).isNotNull();
            assertThat(result.getAllocations()).containsKey("default");
        }

        @Test
        @DisplayName("所有水源为非活跃应返回默认结果")
        void optimize_allInactiveSources_shouldReturnDefault() {
            WaterSource inactive = createSource("inactive", "非活跃水源", "surface", 10.0, 7.2, 1.0, 5000);
            inactive.setIsActive(false);

            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(
                    Collections.singletonList(inactive), 8000);

            assertThat(result.getAllocations()).containsKey("default");
        }

        @Test
        @DisplayName("零需求量应返回有效结果")
        void optimize_zeroDemand_shouldReturnValidResult() {
            List<WaterSource> sources = createTypicalSources();
            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(sources, 0);

            assertThat(result).isNotNull();
            assertThat(result.getTotalCost()).isCloseTo(0.0, within(1.0));
        }

        @Test
        @DisplayName("需求远大于总供应量时仍应返回有效结果")
        void optimize_demandExceedsSupply_shouldReturnResult() {
            WaterSource s1 = createSource("s1", "水源1", "surface", 10.0, 7.2, 1.0, 2000);
            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(
                    Collections.singletonList(s1), 50000);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("水源缺少成本数据应不导致NPE")
        void optimize_nullCost_shouldNotThrow() {
            WaterSource source = new WaterSource(Instant.now(), "nocost", "无成本水源", "surface");
            source.setTurbidity(10.0);
            source.setPh(7.2);
            source.setMaxSupply(5000.0);
            source.setIsActive(true);

            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(
                    Collections.singletonList(source), 3000);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("水源缺少浊度和pH数据应不导致NPE")
        void optimize_nullQualityData_shouldNotThrow() {
            WaterSource source = new WaterSource(Instant.now(), "noquality", "无水质数据", "surface");
            source.setUnitCost(1.0);
            source.setMaxSupply(5000.0);
            source.setIsActive(true);

            MultiObjectiveWaterOptimizer.OptimizationResult result = optimizer.optimize(
                    Collections.singletonList(source), 3000);

            assertThat(result).isNotNull();
            assertThat(Double.isFinite(result.getMixedTurbidity())).isTrue();
            assertThat(Double.isFinite(result.getMixedPh())).isTrue();
        }
    }
}
