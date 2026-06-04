package com.water.dosing.service;

import com.water.dosing.entity.WaterDistribution;
import com.water.dosing.entity.WaterSource;
import com.water.dosing.module.watersource.WaterSourceOptimizer;
import com.water.dosing.module.watersource.dto.OptimizationResultDto;
import com.water.dosing.repository.WaterDistributionRepository;
import com.water.dosing.repository.WaterSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class WaterDistributionService {

    private static final Logger log = LoggerFactory.getLogger(WaterDistributionService.class);

    private final WaterSourceRepository sourceRepo;
    private final WaterDistributionRepository distributionRepo;
    private final WaterSourceOptimizer moduleOptimizer;

    public WaterDistributionService(WaterSourceRepository sourceRepo,
                                     WaterDistributionRepository distributionRepo,
                                     WaterSourceOptimizer moduleOptimizer) {
        this.sourceRepo = sourceRepo;
        this.distributionRepo = distributionRepo;
        this.moduleOptimizer = moduleOptimizer;
    }

    @PostConstruct
    public void init() {
        log.info("[WaterDistributionService] Delegating to WaterSourceOptimizer module");
    }

    @Deprecated
    public void initializeDefaultSources() {
        List<WaterSource> active = sourceRepo.findAllActive();
        if (active.isEmpty()) {
            log.info("[WaterDistributionService] No active sources, initialization handled by module");
        }
    }

    public List<WaterSource> getAllSources() {
        return moduleOptimizer.getAllSources();
    }

    public Map<String, WaterSource> getLatestSourceData() {
        return moduleOptimizer.getLatestSourceData();
    }

    public List<WaterDistribution> getCurrentDistribution() {
        return distributionRepo.findCurrentDistribution();
    }

    @Transactional
    public Map<String, Object> runOptimization() {
        OptimizationResultDto dto = moduleOptimizer.runOptimization();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("optimizationId", dto.getOptimizationId());
        result.put("optimizationTime", dto.getOptimizationTime());
        result.put("totalDemand", dto.getTotalDemand());
        result.put("totalCost", dto.getTotalCost());
        result.put("mixedTurbidity", dto.getMixedTurbidity());
        result.put("mixedPh", dto.getMixedPh());
        result.put("feasible", dto.isFeasible());
        result.put("fitnessScore", dto.getFitnessScore());
        result.put("allocations", dto.getAllocations());
        result.put("usedSwitchConstraint", dto.isUsedSwitchConstraint());
        result.put("switchConstraintNote", dto.getSwitchConstraintNote());
        result.put("delegatedTo", "WaterSourceOptimizer");
        return result;
    }

    public Map<String, Object> getOptimizationStatus() {
        return moduleOptimizer.getOptimizationStatus();
    }

    public List<WaterSource> get24HourSourceHistory(String sourceCode) {
        Instant start = Instant.now().minus(24, ChronoUnit.HOURS);
        return sourceRepo.findBySourceCodeOrderByTimeDesc(sourceCode).stream()
                .filter(s -> s.getTime().isAfter(start))
                .limit(100)
                .collect(java.util.stream.Collectors.toList());
    }

    public WaterSource updateSource(WaterSource source) {
        return sourceRepo.save(source);
    }

    public List<Map<String, Object>> getDailyCostTrend(int days) {
        return moduleOptimizer.getDailyCostTrend(days);
    }
}
