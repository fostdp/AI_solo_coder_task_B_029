package com.water.dosing.config;

import com.water.dosing.predictor.DosingPredictor;
import com.water.dosing.poller.ModbusPoller;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ModbusHealthIndicator implements HealthIndicator {

    private final ModbusPoller modbusPoller;
    private final DosingPredictor dosingPredictor;

    public ModbusHealthIndicator(ModbusPoller modbusPoller, DosingPredictor dosingPredictor) {
        this.modbusPoller = modbusPoller;
        this.dosingPredictor = dosingPredictor;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        try {
            Map<String, Object> pollerStatus = modbusPoller.getStatus();
            builder.withDetail("modbus", pollerStatus);

            Map<String, Object> modelInfo = dosingPredictor.getModelInfo();
            String modelStatus = (String) modelInfo.get("status");
            boolean modelUsable = (boolean) modelInfo.getOrDefault("usable", false);
            builder.withDetail("model", Map.of(
                    "status", modelStatus,
                    "usable", modelUsable,
                    "degraded", modelInfo.getOrDefault("degradedMode", false)
            ));

            if (!modelUsable) {
                builder.down().withDetail("modelIssue", modelInfo.get("statusMessage"));
            }
        } catch (Exception e) {
            builder.down().withException(e);
        }
        return builder.build();
    }
}
