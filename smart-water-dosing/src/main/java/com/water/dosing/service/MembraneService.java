package com.water.dosing.service;

import com.water.dosing.entity.MembraneCleanRecord;
import com.water.dosing.entity.MembraneModule;
import com.water.dosing.module.membrane.MembraneMonitor;
import com.water.dosing.module.membrane.dto.FoulingEvaluationResult;
import com.water.dosing.repository.MembraneCleanRecordRepository;
import com.water.dosing.repository.MembraneModuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MembraneService {

    private static final Logger log = LoggerFactory.getLogger(MembraneService.class);

    private final MembraneModuleRepository moduleRepo;
    private final MembraneCleanRecordRepository cleanRecordRepo;
    private final MembraneMonitor moduleMonitor;

    public MembraneService(MembraneModuleRepository moduleRepo,
                           MembraneCleanRecordRepository cleanRecordRepo,
                           MembraneMonitor moduleMonitor) {
        this.moduleRepo = moduleRepo;
        this.cleanRecordRepo = cleanRecordRepo;
        this.moduleMonitor = moduleMonitor;
    }

    @PostConstruct
    public void init() {
        log.info("[MembraneService] Delegating to MembraneMonitor module");
    }

    @Deprecated
    public void initializeDefaultMembranes() {
        List<MembraneModule> active = moduleRepo.findAllActive();
        if (active.isEmpty()) {
            log.info("[MembraneService] No active membranes, initialization handled by module");
        }
    }

    public Map<String, Object> getAllModulesStatus() {
        return moduleMonitor.getAllModulesStatus();
    }

    public MembraneModule getModuleLatest(String moduleCode) {
        return moduleMonitor.getModuleLatest(moduleCode);
    }

    public List<MembraneModule> getModuleHistory(String moduleCode, int hours) {
        Instant start = Instant.now().minus(hours, ChronoUnit.HOURS);
        return moduleRepo.findByModuleCodeOrderByTimeDesc(moduleCode).stream()
                .filter(m -> m.getTime().isAfter(start))
                .limit(200)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getFoulingTrend(String moduleCode, int hours) {
        return moduleMonitor.getFoulingTrend(moduleCode, hours);
    }

    @Transactional
    public Map<String, Object> evaluateFoulingAndPredictCleaning() {
        FoulingEvaluationResult eval = moduleMonitor.evaluateFoulingAndPredictCleaning();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evaluationId", eval.getEvaluationId());
        result.put("evaluationTime", eval.getEvaluationTime());
        result.put("totalModules", eval.getTotalModules());
        result.put("urgentCount", eval.getUrgentCount());
        result.put("soonCount", eval.getSoonCount());
        result.put("normalCount", eval.getNormalCount());
        result.put("usedStreamProcessing", eval.isUsedStreamProcessing());
        result.put("processingMode", eval.getProcessingMode());
        result.put("delegatedTo", "MembraneMonitor");
        return result;
    }

    @Transactional
    public MembraneCleanRecord recordCleaning(MembraneCleanRecord record) {
        return moduleMonitor.recordCleaning(record);
    }

    public List<MembraneCleanRecord> getCleanHistory(String moduleCode, int days) {
        return moduleMonitor.getCleanHistory(moduleCode, days);
    }

    public Map<String, Object> getCleaningStats(int days) {
        return moduleMonitor.getCleaningStats(days);
    }

    public List<Map<String, Object>> getCleaningSchedule() {
        return moduleMonitor.getCleaningSchedule();
    }

    @Transactional
    public Map<String, Object> recordMembraneReplacement(String moduleCode, String serialNo, String operator) {
        return moduleMonitor.recordMembraneReplacement(moduleCode, serialNo, operator);
    }
}
