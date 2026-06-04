package com.water.dosing.predictor;

import com.water.dosing.entity.DosingRecord;
import com.water.dosing.entity.ModelState;
import com.water.dosing.entity.WaterQuality;
import com.water.dosing.event.DataCollectedEvent;
import com.water.dosing.event.ModelRetrainRequestEvent;
import com.water.dosing.model.PolynomialRegressionModel;
import com.water.dosing.repository.DosingRecordRepository;
import com.water.dosing.repository.ModelStateRepository;
import com.water.dosing.repository.WaterQualityRepository;
import com.water.dosing.service.DosingRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class DosingPredictor {

    private static final Logger log = LoggerFactory.getLogger(DosingPredictor.class);

    public enum ModelStatus {
        NOT_INITIALIZED("模型未初始化", false),
        INSUFFICIENT_DATA("数据量不足", false),
        TRAINING("训练中", false),
        READY("模型就绪", true),
        DEGRADED("降级模式", true),
        FAILED("模型失效", false);

        private final String description;
        private final boolean usable;
        ModelStatus(String d, boolean u) { description=d; usable=u; }
        public String getDescription() { return description; }
        public boolean isUsable() { return usable; }
    }

    private final WaterQualityRepository wqRepo;
    private final DosingRecordRepository dosingRepo;
    private final ModelStateRepository modelStateRepo;

    @Value("${dosing.model.min-samples:50}")
    private int minSamples;

    @Value("${dosing.model.train-window-hours:72}")
    private int trainWindowHours;

    @Value("${dosing.model.enable-degraded-mode:true}")
    private boolean enableDegradedMode;

    @Value("${dosing.model.degraded-base-dose:5.0}")
    private double degradedBaseDose;

    @Value("${dosing.model.ridge-lambda:0.01}")
    private double ridgeLambda;

    @Value("${dosing.model.base-coeff:2.5}")
    private double baseCoeff;

    @Value("${dosing.model.turb-coeff:0.15}")
    private double turbCoeff;

    @Value("${dosing.model.flow-coeff:0.001}")
    private double flowCoeff;

    @Value("${dosing.model.cross-coeff:0.000005}")
    private double crossCoeff;

    private final AtomicReference<ModelStatus> modelStatus = new AtomicReference<>(ModelStatus.NOT_INITIALIZED);
    private volatile String statusMessage = "模型正在初始化";
    private volatile int lastDataCount = 0;

    private PolynomialRegressionModel activeModel = new PolynomialRegressionModel();
    private final Object modelLock = new Object();

    public DosingPredictor(WaterQualityRepository wqRepo, DosingRecordRepository dosingRepo,
                           ModelStateRepository modelStateRepo) {
        this.wqRepo = wqRepo;
        this.dosingRepo = dosingRepo;
        this.modelStateRepo = modelStateRepo;
    }

    @PostConstruct
    public void init() {
        log.info("DosingPredictor init: minSamples={}, window={}h, ridgeLambda={}", minSamples, trainWindowHours, ridgeLambda);
        loadLatestModel();
    }

    @EventListener
    public void onDataCollected(DataCollectedEvent event) {
        List<WaterQuality> wqList = event.getWaterQualityList();
        Map<String, float[]> rawData = event.getRawRegisterData();

        WaterQuality rawWq = wqList.stream()
                .filter(w -> "raw_water".equals(w.getStage()))
                .findFirst().orElse(null);
        float[] rawRegs = rawData.get("raw_water");

        if (rawWq == null || rawRegs == null) return;
        if (rawWq.getTurbidity() == null || rawWq.getFlowRate() == null) return;

        double predicted = predictDose(rawWq.getTurbidity(), rawWq.getFlowRate());
        double actual = rawRegs[6];
        double deviation = actual > 0 ? ((actual - predicted) / actual) * 100 : 0;

        DosingRecord dr = new DosingRecord();
        dr.setTime(Instant.now());
        dr.setStage("flocculation");
        dr.setCoagulantDose(actual);
        dr.setChlorineDose((double) rawRegs[7]);
        dr.setActualDose(actual);
        dr.setPredictedDose(predicted);
        dr.setDeviationPct(deviation);
        dosingRepo.save(dr);
    }

    @EventListener
    public void onModelRetrainRequest(ModelRetrainRequestEvent event) {
        log.info("Model retrain triggered by: {}", event.getTrigger());
        retrainModel();
    }

    private void loadLatestModel() {
        modelStateRepo.findTopByOrderByUpdatedAtDesc().ifPresent(state -> {
            try {
                PolynomialRegressionModel m = new PolynomialRegressionModel();
                double[] coeffs = parseDoubles(state.getCoefficients());
                double[] means = parseDoubles(state.getFeatureMeans());
                double[] stds = parseDoubles(state.getFeatureStds());

                setField(m, "coefficients", coeffs);
                setField(m, "intercept", state.getIntercept());
                setField(m, "featureMeans", means);
                setField(m, "featureStds", stds);
                setField(m, "trained", true);

                synchronized (modelLock) {
                    activeModel = m;
                    modelStatus.set(ModelStatus.READY);
                    statusMessage = String.format("模型已加载，R²=%.3f", state.getRSquared());
                }
                log.info("Loaded model from {}", state.getUpdatedAt());
            } catch (Exception e) {
                log.warn("Model load failed, entering degraded mode", e);
                enterDegradedMode("模型加载失败");
            }
        });
        if (modelStatus.get() == ModelStatus.NOT_INITIALIZED && enableDegradedMode) {
            enterDegradedMode("无历史模型");
        }
    }

    public void retrainModel() {
        if (!modelStatus.compareAndSet(ModelStatus.READY, ModelStatus.TRAINING) &&
                !modelStatus.compareAndSet(ModelStatus.DEGRADED, ModelStatus.TRAINING) &&
                !modelStatus.compareAndSet(ModelStatus.INSUFFICIENT_DATA, ModelStatus.TRAINING) &&
                !modelStatus.compareAndSet(ModelStatus.NOT_INITIALIZED, ModelStatus.TRAINING)) {
            log.warn("Cannot retrain in state {}", modelStatus.get());
            return;
        }
        statusMessage = "模型训练中...";
        try {
            Instant start = Instant.now().minus(trainWindowHours, ChronoUnit.HOURS);
            List<WaterQuality> rawWater = wqRepo.findByTimeBetweenOrderByTimeAsc(start, Instant.now()).stream()
                    .filter(w -> "raw_water".equals(w.getStage()))
                    .collect(Collectors.toList());

            lastDataCount = rawWater.size();
            if (rawWater.size() < minSamples) {
                String msg = String.format("数据不足: %d/%d", rawWater.size(), minSamples);
                log.warn(msg);
                enterDegradedMode(msg);
                return;
            }

            List<double[]> features = new ArrayList<>();
            List<Double> targets = new ArrayList<>();
            for (WaterQuality wq : rawWater) {
                double turb = wq.getTurbidity() != null ? wq.getTurbidity() : 0;
                double flow = wq.getFlowRate() != null ? wq.getFlowRate() : 0;
                if (turb <= 0 || flow <= 0) continue;
                features.add(new double[]{turb, flow});
                targets.add(calculateBaseDose(turb, flow));
            }

            if (features.size() < minSamples) {
                enterDegradedMode(String.format("有效样本不足: %d/%d", features.size(), minSamples));
                return;
            }

            PolynomialRegressionModel newModel = new PolynomialRegressionModel();
            newModel.train(features, targets);

            if (newModel.isTrained()) {
                synchronized (modelLock) {
                    activeModel = newModel;
                    saveModelState(newModel);
                    modelStatus.set(ModelStatus.READY);
                    statusMessage = String.format("训练完成: %d样本, R²=%.3f, MAE=%.3f",
                            features.size(), newModel.getRSquared(), newModel.getMae());
                }
                log.info("Model retrained: R²={}, MAE={}", newModel.getRSquared(), newModel.getMae());
            } else {
                handleTrainFailure("训练未收敛");
            }
        } catch (Exception e) {
            log.error("Retrain failed", e);
            handleTrainFailure("训练异常: " + e.getMessage());
        }
    }

    private void handleTrainFailure(String reason) {
        if (enableDegradedMode) {
            enterDegradedMode(reason);
        } else {
            modelStatus.set(ModelStatus.FAILED);
            statusMessage = reason;
        }
    }

    private void enterDegradedMode(String reason) {
        modelStatus.set(ModelStatus.DEGRADED);
        statusMessage = "降级模式 - " + reason;
        log.warn("Degraded mode: {}", reason);
    }

    private double calculateBaseDose(double turbidity, double flowRate) {
        return baseCoeff + turbCoeff * turbidity + flowCoeff * flowRate + crossCoeff * turbidity * flowRate;
    }

    public double predictDose(double turbidity, double flowRate) {
        ModelStatus st = modelStatus.get();
        if (st == ModelStatus.DEGRADED || st == ModelStatus.NOT_INITIALIZED || !st.isUsable()) {
            return Math.max(0, degradedBaseDose + turbCoeff * turbidity + flowCoeff * flowRate);
        }
        synchronized (modelLock) {
            return activeModel.predict(turbidity, flowRate);
        }
    }

    public boolean isModelReady() { return modelStatus.get().isUsable(); }

    public Map<String, Object> getModelInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", modelStatus.get().name());
        info.put("statusText", modelStatus.get().getDescription());
        info.put("statusMessage", statusMessage);
        info.put("usable", modelStatus.get().isUsable());
        info.put("minSamples", minSamples);
        info.put("lastDataCount", lastDataCount);
        info.put("trainWindowHours", trainWindowHours);
        info.put("degradedMode", modelStatus.get() == ModelStatus.DEGRADED);
        info.put("ridgeLambda", ridgeLambda);
        info.put("baseCoeff", baseCoeff);
        info.put("turbCoeff", turbCoeff);
        info.put("flowCoeff", flowCoeff);
        if (modelStatus.get() == ModelStatus.READY) {
            synchronized (modelLock) {
                info.put("rSquared", activeModel.getRSquared());
                info.put("mae", activeModel.getMae());
            }
        }
        return info;
    }

    private void saveModelState(PolynomialRegressionModel model) {
        ModelState state = new ModelState();
        state.setUpdatedAt(Instant.now());
        state.setModelType("polynomial_regression");
        state.setCoefficients(model.serializeCoefficients());
        state.setIntercept(model.getIntercept());
        state.setFeatureMeans(model.serializeArray(model.getFeatureMeans()));
        state.setFeatureStds(model.serializeArray(model.getFeatureStds()));
        state.setRSquared(model.getRSquared());
        state.setMae(model.getMae());
        modelStateRepo.save(state);
    }

    private double[] parseDoubles(String csv) {
        String[] parts = csv.split(",");
        double[] arr = new double[parts.length];
        for (int i = 0; i < parts.length; i++) arr[i] = Double.parseDouble(parts[i]);
        return arr;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
