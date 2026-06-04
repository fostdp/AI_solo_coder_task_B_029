package com.water.dosing.module.watersource;

import com.water.dosing.entity.WaterDistribution;
import com.water.dosing.entity.WaterSource;
import com.water.dosing.module.watersource.dto.OptimizationResultDto;
import com.water.dosing.optimizer.MultiObjectiveWaterOptimizer;
import com.water.dosing.repository.WaterDistributionRepository;
import com.water.dosing.repository.WaterSourceRepository;
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
import java.util.stream.Collectors;

@Component
public class WaterSourceOptimizer {

    private static final Logger log = LoggerFactory.getLogger(WaterSourceOptimizer.class);
    private static final String MODULE_NAME = "WaterSourceOptimizer";
    private static final String MODULE_VERSION = "1.0.0";

    private final WaterSourceRepository sourceRepo;
    private final WaterDistributionRepository distributionRepo;
    private final MultiObjectiveWaterOptimizer optimizer;
    private final WaterSourceOptimizerConfig config;

    public WaterSourceOptimizer(WaterSourceRepository sourceRepo,
                                 WaterDistributionRepository distributionRepo,
                                 MultiObjectiveWaterOptimizer optimizer,
                                 WaterSourceOptimizerConfig config) {
        this.sourceRepo = sourceRepo;
        this.distributionRepo = distributionRepo;
        this.optimizer = optimizer;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        log.info("[{}] Initializing module version {}", MODULE_NAME, MODULE_VERSION);
        List<WaterSource> active = sourceRepo.findAllActive();
        if (active.isEmpty()) {
            initializeDefaultSources();
        }
        optimizer.setSwitchPenaltyWeight(config.getSwitchPenaltyWeight());
        optimizer.setEnforceSwitchDelay(config.isEnforceSwitchDelay());
        log.info("[{}] Module initialized successfully", MODULE_NAME);
    }

    private void initializeDefaultSources() {
        log.info("[{}] Initializing default water sources", MODULE_NAME);
        Instant now = Instant.now();

        WaterSource reservoir = new WaterSource(now, "reservoir", "水库水源", "surface");
        reservoir.setTurbidity(12.0);
        reservoir.setPh(7.2);
        reservoir.setTemperature(16.0);
        reservoir.setAmmonia(0.08);
        reservoir.setCod(2.0);
        reservoir.setConductivity(350.0);
        reservoir.setHardness(120.0);
        reservoir.setUnitCost(0.85);
        reservoir.setMaxSupply(6000.0);
        reservoir.setCurrentSupply(4000.0);
        sourceRepo.save(reservoir);

        WaterSource groundwater = new WaterSource(now, "groundwater", "地下水源", "ground");
        groundwater.setTurbidity(3.5);
        groundwater.setPh(7.6);
        groundwater.setTemperature(18.5);
        groundwater.setAmmonia(0.03);
        groundwater.setCod(0.8);
        groundwater.setConductivity(650.0);
        groundwater.setHardness(280.0);
        groundwater.setUnitCost(1.35);
        groundwater.setMaxSupply(4000.0);
        groundwater.setCurrentSupply(3000.0);
        sourceRepo.save(groundwater);

        WaterSource river = new WaterSource(now, "river", "河流水源", "surface");
        river.setTurbidity(25.0);
        river.setPh(7.0);
        river.setTemperature(19.0);
        river.setAmmonia(0.15);
        river.setCod(3.5);
        river.setConductivity(420.0);
        river.setHardness(150.0);
        river.setUnitCost(0.65);
        river.setMaxSupply(5000.0);
        river.setCurrentSupply(1333.0);
        sourceRepo.save(river);
    }

    public Map<String, Object> getModuleInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("moduleName", MODULE_NAME);
        info.put("version", MODULE_VERSION);
        info.put("status", "active");
        info.put("totalDemand", config.getTotalDemand());
        info.put("autoOptimize", config.isAutoOptimize());
        info.put("enforceSwitchDelay", config.isEnforceSwitchDelay());
        info.put("switchPenaltyWeight", config.getSwitchPenaltyWeight());
        info.put("activeSources", sourceRepo.findAllActive().size());
        return info;
    }

    public List<WaterSource> getAllSources() {
        return sourceRepo.findAllActive();
    }

    public Map<String, WaterSource> getLatestSourceData() {
        Map<String, WaterSource> result = new LinkedHashMap<>();
        List<WaterSource> active = sourceRepo.findAllActive();
        for (WaterSource source : active) {
            List<WaterSource> latest = sourceRepo.findLatestBySourceCode(source.getSourceCode());
            if (!latest.isEmpty()) {
                result.put(source.getSourceCode(), latest.get(0));
            }
        }
        return result;
    }

    public List<WaterDistribution> getCurrentDistribution() {
        return distributionRepo.findCurrentDistribution();
    }

    @Transactional
    public OptimizationResultDto runOptimization() {
        return runOptimization(null);
    }

    @Transactional
    public OptimizationResultDto runOptimization(Map<String, Double> previousAllocations) {
        log.info("[{}] Starting optimization run", MODULE_NAME);

        List<WaterSource> sources = sourceRepo.findAllActive();
        if (sources.isEmpty()) {
            log.warn("[{}] No active water sources found", MODULE_NAME);
            OptimizationResultDto result = new OptimizationResultDto();
            result.setFeasible(false);
            result.setSwitchConstraintNote("No active water sources");
            return result;
        }

        if (previousAllocations == null) {
            List<WaterDistribution> currentDist = distributionRepo.findCurrentDistribution();
            if (!currentDist.isEmpty()) {
                previousAllocations = new LinkedHashMap<>();
                for (WaterDistribution d : currentDist) {
                    previousAllocations.put(d.getSourceCode(), d.getAllocationRatio());
                }
            }
        }

        optimizer.setMaxIterations(config.getMaxIterations());
        optimizer.setPopulationSize(config.getPopulationSize());

        MultiObjectiveWaterOptimizer.OptimizationResult optResult =
                optimizer.optimize(sources, config.getTotalDemand(), previousAllocations);

        String optId = "OPT-" + System.currentTimeMillis();
        Instant now = Instant.now();

        List<WaterDistribution> current = distributionRepo.findCurrentDistribution();
        for (WaterDistribution d : current) {
            d.setIsCurrent(false);
            distributionRepo.save(d);
        }

        List<OptimizationResultDto.AllocationDetail> allocationDetails = new ArrayList<>();
        List<WaterDistribution> distributions = new ArrayList<>();

        for (Map.Entry<String, Double> entry : optResult.getAllocations().entrySet()) {
            String sourceCode = entry.getKey();
            double ratio = entry.getValue();
            WaterSource source = sources.stream()
                    .filter(s -> s.getSourceCode().equals(sourceCode))
                    .findFirst().orElse(null);

            WaterDistribution dist = new WaterDistribution(now, sourceCode,
                    source != null ? source.getSourceName() : sourceCode);
            dist.setAllocationRatio(ratio);
            dist.setAllocationVolume(ratio * config.getTotalDemand());
            dist.setUnitCost(source != null ? source.getUnitCost() : 0);
            dist.setTotalCost(ratio * config.getTotalDemand() *
                    (source != null && source.getUnitCost() != null ? source.getUnitCost() : 0));
            dist.setMixTurbidity(optResult.getMixedTurbidity());
            dist.setMixPh(optResult.getMixedPh());
            dist.setOptimizationId(optId);
            dist.setIsCurrent(true);
            distributions.add(dist);

            allocationDetails.add(new OptimizationResultDto.AllocationDetail(
                    sourceCode,
                    source != null ? source.getSourceName() : sourceCode,
                    Math.round(ratio * 10000.0) / 100.0,
                    Math.round(ratio * config.getTotalDemand() * 100.0) / 100.0,
                    source != null ? source.getUnitCost() : 0,
                    Math.round(dist.getTotalCost() * 100.0) / 100.0
            ));
        }
        distributionRepo.saveAll(distributions);

        OptimizationResultDto result = new OptimizationResultDto();
        result.setOptimizationId(optId);
        result.setOptimizationTime(now);
        result.setTotalDemand(config.getTotalDemand());
        result.setTotalCost(Math.round(optResult.getTotalCost() * 100.0) / 100.0);
        result.setMixedTurbidity(Math.round(optResult.getMixedTurbidity() * 100.0) / 100.0);
        result.setMixedPh(Math.round(optResult.getMixedPh() * 100.0) / 100.0);
        result.setFeasible(optResult.isFeasible());
        result.setFitnessScore(Math.round(optResult.getFitnessScore() * 100.0) / 100.0);
        result.setAllocations(optResult.getAllocations());
        result.setAllocationDetails(allocationDetails);
        result.setUsedSwitchConstraint(config.isEnforceSwitchDelay() && previousAllocations != null);
        if (result.isUsedSwitchConstraint()) {
            result.setSwitchConstraintNote(String.format(
                    "切换延迟约束已启用，惩罚权重%.2f", config.getSwitchPenaltyWeight()));
        }

        log.info("[{}] Optimization complete: cost={}, feasible={}",
                MODULE_NAME, result.getTotalCost(), result.isFeasible());

        return result;
    }

    @Async("moduleTaskExecutor")
    public CompletableFuture<OptimizationResultDto> runOptimizationAsync() {
        log.info("[{}] Starting async optimization", MODULE_NAME);
        return CompletableFuture.completedFuture(runOptimization());
    }

    public Map<String, Object> getOptimizationStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<WaterDistribution> current = distributionRepo.findCurrentDistribution();

        if (current.isEmpty()) {
            result.put("optimized", false);
            result.put("module", MODULE_NAME);
            result.put("moduleVersion", MODULE_VERSION);
            return result;
        }

        WaterDistribution first = current.get(0);
        double totalCost = current.stream()
                .mapToDouble(d -> d.getTotalCost() != null ? d.getTotalCost() : 0)
                .sum();

        result.put("module", MODULE_NAME);
        result.put("moduleVersion", MODULE_VERSION);
        result.put("optimized", true);
        result.put("optimizationId", first.getOptimizationId());
        result.put("optimizationTime", first.getTime().toString());
        result.put("totalDemand", config.getTotalDemand());
        result.put("totalCost", Math.round(totalCost * 100.0) / 100.0);
        result.put("mixedTurbidity", first.getMixTurbidity() != null ?
                Math.round(first.getMixTurbidity() * 100.0) / 100.0 : null);
        result.put("mixedPh", first.getMixPh() != null ?
                Math.round(first.getMixPh() * 100.0) / 100.0 : null);

        List<Map<String, Object>> allocationList = new ArrayList<>();
        for (WaterDistribution d : current) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceCode", d.getSourceCode());
            item.put("sourceName", d.getSourceName());
            item.put("ratio", Math.round(d.getAllocationRatio() * 10000.0) / 100.0);
            item.put("volume", Math.round(d.getAllocationVolume() * 100.0) / 100.0);
            allocationList.add(item);
        }
        result.put("allocations", allocationList);

        return result;
    }

    public List<WaterSource> get24HourSourceHistory(String sourceCode) {
        Instant start = Instant.now().minus(24, ChronoUnit.HOURS);
        return sourceRepo.findBySourceCodeOrderByTimeDesc(sourceCode).stream()
                .filter(s -> s.getTime().isAfter(start))
                .limit(100)
                .collect(Collectors.toList());
    }

    public WaterSource updateSource(WaterSource source) {
        return sourceRepo.save(source);
    }

    public List<Map<String, Object>> getDailyCostTrend(int days) {
        Instant start = Instant.now().minus(days, ChronoUnit.DAYS);
        List<WaterDistribution> dists = distributionRepo.findRecentDistributions(start);

        Map<String, List<WaterDistribution>> byDay = new LinkedHashMap<>();
        for (WaterDistribution d : dists) {
            String day = d.getTime().toString().substring(0, 10);
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(d);
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, List<WaterDistribution>> entry : byDay.entrySet()) {
            double totalCost = entry.getValue().stream()
                    .mapToDouble(d -> d.getTotalCost() != null ? d.getTotalCost() : 0)
                    .sum();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.getKey());
            item.put("totalCost", Math.round(totalCost * 100.0) / 100.0);
            item.put("module", MODULE_NAME);
            trend.add(item);
        }
        return trend;
    }
}
