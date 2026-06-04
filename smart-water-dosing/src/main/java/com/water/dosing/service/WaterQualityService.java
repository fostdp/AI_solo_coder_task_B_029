package com.water.dosing.service;

import com.water.dosing.entity.WaterQuality;
import com.water.dosing.repository.WaterQualityRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WaterQualityService {

    private final WaterQualityRepository repository;

    public WaterQualityService(WaterQualityRepository repository) {
        this.repository = repository;
    }

    public WaterQuality save(WaterQuality wq) {
        return repository.save(wq);
    }

    public List<WaterQuality> saveAll(List<WaterQuality> list) {
        return repository.saveAll(list);
    }

    public List<WaterQuality> getLatestByStage(String stage) {
        List<WaterQuality> list = repository.findLatestByStage(stage);
        if (list.isEmpty()) return list;
        return list.stream().limit(1).collect(Collectors.toList());
    }

    public Map<String, WaterQuality> getAllLatest() {
        Map<String, WaterQuality> result = new LinkedHashMap<>();
        String[] stages = {"raw_water", "flocculation", "sedimentation", "filtration", "outlet"};
        for (String stage : stages) {
            List<WaterQuality> list = repository.findLatestByStage(stage);
            if (!list.isEmpty()) {
                result.put(stage, list.get(0));
            }
        }
        return result;
    }

    public List<WaterQuality> get24HourHistory(String stage) {
        Instant start = Instant.now().minus(24, ChronoUnit.HOURS);
        return repository.findHistoryByStage(stage, start);
    }

    public List<WaterQuality> get72HourHistory(String stage) {
        Instant start = Instant.now().minus(72, ChronoUnit.HOURS);
        return repository.findHistoryByStage(stage, start);
    }

    public String getStatus(String stage, Double turbidity) {
        if (turbidity == null) return "unknown";
        switch (stage) {
            case "raw_water":
                return turbidity <= 100 ? "normal" : turbidity <= 200 ? "warning" : "alarm";
            case "flocculation":
                return turbidity <= 15 ? "normal" : turbidity <= 25 ? "warning" : "alarm";
            case "sedimentation":
                return turbidity <= 3 ? "normal" : turbidity <= 5 ? "warning" : "alarm";
            case "filtration":
                return turbidity <= 0.5 ? "normal" : turbidity <= 1.0 ? "warning" : "alarm";
            case "outlet":
                return turbidity <= 0.5 ? "normal" : turbidity <= 1.0 ? "warning" : "alarm";
            default:
                return "unknown";
        }
    }
}
