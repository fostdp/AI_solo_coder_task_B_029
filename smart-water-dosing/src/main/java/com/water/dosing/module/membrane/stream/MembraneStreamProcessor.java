package com.water.dosing.module.membrane.stream;

import com.water.dosing.entity.MembraneModule;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MembraneStreamProcessor {

    private static final Logger log = LoggerFactory.getLogger(MembraneStreamProcessor.class);
    private static final String MODULE_NAME = "MembraneStreamProcessor";

    private StreamExecutionEnvironment env;
    private boolean running = false;
    private final Map<String, MembraneStreamResult> latestResults = new ConcurrentHashMap<>();
    private final AtomicLong processedRecords = new AtomicLong(0);
    private final AtomicLong detectionEvents = new AtomicLong(0);

    public static class MembraneStreamResult implements Serializable {
        private String moduleCode;
        private double avgFlux;
        private double avgTmd;
        private double fluxChangeRate;
        private double tmdChangeRate;
        private double foulingTrend;
        private boolean potentialReplacement;
        private boolean alertTriggered;
        private Instant lastUpdate;
        private long windowSize;

        public MembraneStreamResult() {}

        public String getModuleCode() { return moduleCode; }
        public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }
        public double getAvgFlux() { return avgFlux; }
        public void setAvgFlux(double avgFlux) { this.avgFlux = avgFlux; }
        public double getAvgTmd() { return avgTmd; }
        public void setAvgTmd(double avgTmd) { this.avgTmd = avgTmd; }
        public double getFluxChangeRate() { return fluxChangeRate; }
        public void setFluxChangeRate(double fluxChangeRate) { this.fluxChangeRate = fluxChangeRate; }
        public double getTmdChangeRate() { return tmdChangeRate; }
        public void setTmdChangeRate(double tmdChangeRate) { this.tmdChangeRate = tmdChangeRate; }
        public double getFoulingTrend() { return foulingTrend; }
        public void setFoulingTrend(double foulingTrend) { this.foulingTrend = foulingTrend; }
        public boolean isPotentialReplacement() { return potentialReplacement; }
        public void setPotentialReplacement(boolean potentialReplacement) { this.potentialReplacement = potentialReplacement; }
        public boolean isAlertTriggered() { return alertTriggered; }
        public void setAlertTriggered(boolean alertTriggered) { this.alertTriggered = alertTriggered; }
        public Instant getLastUpdate() { return lastUpdate; }
        public void setLastUpdate(Instant lastUpdate) { this.lastUpdate = lastUpdate; }
        public long getWindowSize() { return windowSize; }
        public void setWindowSize(long windowSize) { this.windowSize = windowSize; }
    }

    public void startStreamProcessing(List<MembraneModule> modules, int parallelism) {
        if (running) {
            log.warn("[{}] Stream processing already running", MODULE_NAME);
            return;
        }

        try {
            log.info("[{}] Starting Flink stream processing with parallelism={}", MODULE_NAME, parallelism);
            env = StreamExecutionEnvironment.getExecutionEnvironment();
            env.setParallelism(parallelism);

            DataStream<MembraneModule> sourceStream = env.fromCollection(modules);

            KeyedStream<MembraneModule, String> keyedStream = sourceStream
                    .keyBy(MembraneModule::getModuleCode);

            DataStream<MembraneStreamResult> resultStream = keyedStream
                    .map(new MembraneAnalysisRichFunction());

            resultStream.map(new MapFunction<MembraneStreamResult, MembraneStreamResult>() {
                @Override
                public MembraneStreamResult map(MembraneStreamResult result) throws Exception {
                    latestResults.put(result.getModuleCode(), result);
                    processedRecords.incrementAndGet();
                    if (result.isAlertTriggered() || result.isPotentialReplacement()) {
                        detectionEvents.incrementAndGet();
                    }
                    return result;
                }
            });

            env.executeAsync("Membrane Fouling Stream Analysis");
            running = true;
            log.info("[{}] Stream processing started successfully", MODULE_NAME);
        } catch (Exception e) {
            log.error("[{}] Failed to start stream processing", MODULE_NAME, e);
            running = false;
        }
    }

    private static class MembraneAnalysisRichFunction extends RichMapFunction<MembraneModule, MembraneStreamResult> {

        private transient ValueState<Double> baselineFluxState;
        private transient ValueState<Double> baselineTmdState;
        private transient ValueState<Double> previousFluxState;
        private transient ValueState<Double> previousTmdState;
        private transient ValueState<Long> countState;

        @Override
        public void open(Configuration parameters) throws Exception {
            ValueStateDescriptor<Double> baselineFluxDesc =
                    new ValueStateDescriptor<>("baselineFlux", Double.class);
            ValueStateDescriptor<Double> baselineTmdDesc =
                    new ValueStateDescriptor<>("baselineTmd", Double.class);
            ValueStateDescriptor<Double> previousFluxDesc =
                    new ValueStateDescriptor<>("previousFlux", Double.class);
            ValueStateDescriptor<Double> previousTmdDesc =
                    new ValueStateDescriptor<>("previousTmd", Double.class);
            ValueStateDescriptor<Long> countDesc =
                    new ValueStateDescriptor<>("recordCount", Long.class);

            baselineFluxState = getRuntimeContext().getState(baselineFluxDesc);
            baselineTmdState = getRuntimeContext().getState(baselineTmdDesc);
            previousFluxState = getRuntimeContext().getState(previousFluxDesc);
            previousTmdState = getRuntimeContext().getState(previousTmdDesc);
            countState = getRuntimeContext().getState(countDesc);
        }

        @Override
        public MembraneStreamResult map(MembraneModule module) throws Exception {
            Double baselineFlux = baselineFluxState.value();
            Double baselineTmd = baselineTmdState.value();
            Double previousFlux = previousFluxState.value();
            Double previousTmd = previousTmdState.value();
            Long count = countState.value();

            if (count == null) count = 0L;
            count++;

            if (baselineFlux == null) {
                baselineFlux = module.getFlux() != null ? module.getFlux() : 60.0;
                baselineFluxState.update(baselineFlux);
            }
            if (baselineTmd == null) {
                baselineTmd = module.getTmd() != null ? module.getTmd() : 1.0;
                baselineTmdState.update(baselineTmd);
            }

            double currentFlux = module.getFlux() != null ? module.getFlux() : baselineFlux;
            double currentTmd = module.getTmd() != null ? module.getTmd() : baselineTmd;

            double fluxChangeRate = previousFlux != null && previousFlux > 0 ?
                    (currentFlux - previousFlux) / previousFlux : 0;
            double tmdChangeRate = previousTmd != null && previousTmd > 0 ?
                    (currentTmd - previousTmd) / previousTmd : 0;

            double avgFlux = (baselineFlux * (count - 1) + currentFlux) / count;
            double avgTmd = (baselineTmd * (count - 1) + currentTmd) / count;

            double foulingIndex = currentFlux / baselineFlux;
            boolean potentialReplacement = fluxChangeRate > 0.2 && tmdChangeRate < -0.2;
            boolean alertTriggered = foulingIndex < 0.5;

            previousFluxState.update(currentFlux);
            previousTmdState.update(currentTmd);
            countState.update(count);

            MembraneStreamResult result = new MembraneStreamResult();
            result.setModuleCode(module.getModuleCode());
            result.setAvgFlux(Math.round(avgFlux * 100.0) / 100.0);
            result.setAvgTmd(Math.round(avgTmd * 100.0) / 100.0);
            result.setFluxChangeRate(Math.round(fluxChangeRate * 10000.0) / 100.0);
            result.setTmdChangeRate(Math.round(tmdChangeRate * 10000.0) / 100.0);
            result.setFoulingTrend(Math.round(foulingIndex * 100.0) / 100.0);
            result.setPotentialReplacement(potentialReplacement);
            result.setAlertTriggered(alertTriggered);
            result.setLastUpdate(Instant.now());
            result.setWindowSize(count);

            return result;
        }
    }

    public void processModuleData(MembraneModule module) {
        processedRecords.incrementAndGet();

        MembraneStreamResult result = new MembraneStreamResult();
        result.setModuleCode(module.getModuleCode());

        MembraneStreamResult previous = latestResults.get(module.getModuleCode());
        double baselineFlux = module.getBaselineFlux() != null ? module.getBaselineFlux() :
                (previous != null ? previous.getAvgFlux() : 60.0);
        double baselineTmd = module.getBaselineTmd() != null ? module.getBaselineTmd() :
                (previous != null ? previous.getAvgTmd() : 1.0);

        double currentFlux = module.getFlux() != null ? module.getFlux() : baselineFlux;
        double currentTmd = module.getTmd() != null ? module.getTmd() : baselineTmd;

        double fluxChangeRate = previous != null && previous.getAvgFlux() > 0 ?
                (currentFlux - previous.getAvgFlux()) / previous.getAvgFlux() : 0;
        double tmdChangeRate = previous != null && previous.getAvgTmd() > 0 ?
                (currentTmd - previous.getAvgTmd()) / previous.getAvgTmd() : 0;

        long windowSize = previous != null ? previous.getWindowSize() + 1 : 1;
        double avgFlux = previous != null ?
                (previous.getAvgFlux() * (windowSize - 1) + currentFlux) / windowSize : currentFlux;
        double avgTmd = previous != null ?
                (previous.getAvgTmd() * (windowSize - 1) + currentTmd) / windowSize : currentTmd;

        double foulingIndex = currentFlux / baselineFlux;
        boolean potentialReplacement = fluxChangeRate > 0.2 && tmdChangeRate < -0.2;
        boolean alertTriggered = foulingIndex < 0.5;

        if (potentialReplacement || alertTriggered) {
            detectionEvents.incrementAndGet();
        }

        result.setAvgFlux(Math.round(avgFlux * 100.0) / 100.0);
        result.setAvgTmd(Math.round(avgTmd * 100.0) / 100.0);
        result.setFluxChangeRate(Math.round(fluxChangeRate * 10000.0) / 100.0);
        result.setTmdChangeRate(Math.round(tmdChangeRate * 10000.0) / 100.0);
        result.setFoulingTrend(Math.round(foulingIndex * 100.0) / 100.0);
        result.setPotentialReplacement(potentialReplacement);
        result.setAlertTriggered(alertTriggered);
        result.setLastUpdate(Instant.now());
        result.setWindowSize(windowSize);

        latestResults.put(module.getModuleCode(), result);
    }

    public MembraneStreamResult getLatestResult(String moduleCode) {
        return latestResults.get(moduleCode);
    }

    public Map<String, Object> getStreamStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("running", running);
        stats.put("processedRecords", processedRecords.get());
        stats.put("detectionEvents", detectionEvents.get());
        stats.put("activeModules", latestResults.size());
        stats.put("moduleResults", new ArrayList<>(latestResults.values()));
        return stats;
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                log.warn("[{}] Error stopping Flink environment", MODULE_NAME, e);
            }
        }
        running = false;
        log.info("[{}] Stream processing stopped", MODULE_NAME);
    }

    @PreDestroy
    public void destroy() {
        stop();
    }
}
