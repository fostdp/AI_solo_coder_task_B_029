package com.water.dosing.controller;

import com.water.dosing.entity.AlertRecord;
import com.water.dosing.entity.CostIndicator;
import com.water.dosing.entity.DosingRecord;
import com.water.dosing.entity.WaterQuality;
import com.water.dosing.event.AlarmEvent;
import com.water.dosing.event.ModelRetrainRequestEvent;
import com.water.dosing.notifier.AlarmNotifier;
import com.water.dosing.poller.ModbusPoller;
import com.water.dosing.predictor.DosingPredictor;
import com.water.dosing.service.CostIndicatorService;
import com.water.dosing.service.DosingRecordService;
import com.water.dosing.service.WaterQualityService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class WaterDosingController {

    private final WaterQualityService wqService;
    private final DosingRecordService dosingService;
    private final CostIndicatorService costService;
    private final DosingPredictor dosingPredictor;
    private final AlarmNotifier alarmNotifier;
    private final ModbusPoller modbusPoller;
    private final ApplicationEventPublisher eventPublisher;

    public WaterDosingController(WaterQualityService wqService, DosingRecordService dosingService,
                                  CostIndicatorService costService, DosingPredictor dosingPredictor,
                                  AlarmNotifier alarmNotifier, ModbusPoller modbusPoller,
                                  ApplicationEventPublisher eventPublisher) {
        this.wqService = wqService;
        this.dosingService = dosingService;
        this.costService = costService;
        this.dosingPredictor = dosingPredictor;
        this.alarmNotifier = alarmNotifier;
        this.modbusPoller = modbusPoller;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAllStatus() {
        Map<String, WaterQuality> latest = wqService.getAllLatest();
        Map<String, Object> result = new LinkedHashMap<>();
        String[] stages = {"raw_water", "flocculation", "sedimentation", "filtration", "outlet"};
        String[] names = {"原水", "絮凝池", "沉淀池", "滤池", "出水"};
        for (int i = 0; i < stages.length; i++) {
            String stage = stages[i];
            WaterQuality wq = latest.get(stage);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", names[i]);
            d.put("stage", stage);
            if (wq != null) {
                d.put("turbidity", wq.getTurbidity());
                d.put("ph", wq.getPh());
                d.put("temperature", wq.getTemperature());
                d.put("ammonia", wq.getAmmonia());
                d.put("cod", wq.getCod());
                d.put("flowRate", wq.getFlowRate());
                d.put("time", wq.getTime().toString());
                d.put("status", wqService.getStatus(stage, wq.getTurbidity()));
            } else {
                d.put("status", "unknown");
            }
            result.put(stage, d);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/trend/{stage}")
    public ResponseEntity<Map<String, Object>> get24HourTrend(@PathVariable String stage) {
        List<WaterQuality> wqData = wqService.get24HourHistory(stage);
        List<DosingRecord> dosingData = dosingService.get24HourHistory(stage);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", stage);

        List<Map<String, Object>> waterTrend = new ArrayList<>();
        for (WaterQuality wq : wqData) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", wq.getTime().toString());
            p.put("turbidity", wq.getTurbidity());
            p.put("ph", wq.getPh());
            p.put("temperature", wq.getTemperature());
            p.put("ammonia", wq.getAmmonia());
            p.put("cod", wq.getCod());
            waterTrend.add(p);
        }
        result.put("waterQuality", waterTrend);

        List<Map<String, Object>> dosingTrend = new ArrayList<>();
        for (DosingRecord dr : dosingData) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", dr.getTime().toString());
            p.put("actualDose", dr.getActualDose());
            p.put("predictedDose", dr.getPredictedDose());
            p.put("deviationPct", dr.getDeviationPct());
            p.put("coagulantDose", dr.getCoagulantDose());
            p.put("chlorineDose", dr.getChlorineDose());
            dosingTrend.add(p);
        }
        result.put("dosing", dosingTrend);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/dosing/prediction")
    public ResponseEntity<Map<String, Object>> getDosingPrediction() {
        Map<String, WaterQuality> latest = wqService.getAllLatest();
        WaterQuality raw = latest.get("raw_water");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", dosingPredictor.getModelInfo());

        if (raw != null && raw.getTurbidity() != null && raw.getFlowRate() != null) {
            double turbidity = raw.getTurbidity();
            double flowRate = raw.getFlowRate();
            double predicted = dosingPredictor.predictDose(turbidity, flowRate);
            List<DosingRecord> recent = dosingService.get24HourHistory("flocculation");
            double actual = recent.isEmpty() ? predicted :
                    recent.get(recent.size() - 1).getActualDose() != null ? recent.get(recent.size() - 1).getActualDose() : predicted;
            double deviation = actual > 0 ? ((actual - predicted) / actual) * 100 : 0;
            result.put("turbidity", turbidity);
            result.put("flowRate", flowRate);
            result.put("predictedDose", Math.round(predicted * 100.0) / 100.0);
            result.put("actualDose", Math.round(actual * 100.0) / 100.0);
            result.put("deviationPct", Math.round(deviation * 100.0) / 100.0);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/model/status")
    public ResponseEntity<Map<String, Object>> getModelStatus() {
        return ResponseEntity.ok(dosingPredictor.getModelInfo());
    }

    @GetMapping("/cost/trend")
    public ResponseEntity<List<Map<String, Object>>> getCostTrend(@RequestParam(defaultValue = "7") int days) {
        List<CostIndicator> costs = costService.getRecentDays(days);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CostIndicator c : costs) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", c.getTime().toString());
            p.put("alumConsumption", c.getAlumConsumption());
            p.put("chlorineConsumption", c.getChlorineConsumption());
            p.put("electricityConsumption", c.getElectricityConsumption());
            result.add(p);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<AlertRecord>> getAlerts() {
        return ResponseEntity.ok(alarmNotifier.getRecentAlerts());
    }

    @GetMapping("/alerts/unacknowledged")
    public ResponseEntity<List<AlertRecord>> getUnacknowledgedAlerts() {
        return ResponseEntity.ok(alarmNotifier.getUnacknowledgedAlerts());
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public ResponseEntity<AlertRecord> acknowledgeAlert(@PathVariable Integer id) {
        return ResponseEntity.ok(alarmNotifier.acknowledge(id));
    }

    @PostMapping("/model/retrain")
    public ResponseEntity<Map<String, Object>> retrainModel() {
        Map<String, Object> before = dosingPredictor.getModelInfo();
        eventPublisher.publishEvent(new ModelRetrainRequestEvent(this, "manual"));
        Map<String, Object> after = dosingPredictor.getModelInfo();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", dosingPredictor.isModelReady());
        result.put("before", before);
        result.put("after", after);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/system/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("timestamp", System.currentTimeMillis());
        health.put("model", dosingPredictor.getModelInfo());
        health.put("alerts", alarmNotifier.getNotificationStatus());
        health.put("modbus", modbusPoller.getStatus());
        return ResponseEntity.ok(health);
    }

    @PostMapping("/test-alert")
    public ResponseEntity<Map<String, Object>> createTestAlert(
            @RequestParam(defaultValue = "2") int level,
            @RequestParam(defaultValue = "test") String type,
            @RequestParam(defaultValue = "测试告警") String message) {
        eventPublisher.publishEvent(new AlarmEvent(this, level, type, message));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("published", true);
        result.put("level", level);
        result.put("type", type);
        return ResponseEntity.ok(result);
    }
}
