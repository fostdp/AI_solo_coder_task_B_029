package com.water.dosing.scheduler;

import com.water.dosing.entity.CostIndicator;
import com.water.dosing.event.ModelRetrainRequestEvent;
import com.water.dosing.module.chemical.ChemicalInventoryManager;
import com.water.dosing.module.forecast.WaterQualityForecasterManager;
import com.water.dosing.module.membrane.MembraneMonitor;
import com.water.dosing.module.watersource.WaterSourceOptimizer;
import com.water.dosing.repository.CostIndicatorRepository;
import com.water.dosing.service.WaterQualityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class SystemScheduler {

    private static final Logger log = LoggerFactory.getLogger(SystemScheduler.class);

    private final WaterQualityService wqService;
    private final CostIndicatorRepository costRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final WaterSourceOptimizer waterSourceOptimizer;
    private final MembraneMonitor membraneMonitor;
    private final ChemicalInventoryManager chemicalInventoryManager;
    private final WaterQualityForecasterManager forecasterManager;

    public SystemScheduler(WaterQualityService wqService, CostIndicatorRepository costRepo,
                           ApplicationEventPublisher eventPublisher,
                           WaterSourceOptimizer waterSourceOptimizer,
                           MembraneMonitor membraneMonitor,
                           ChemicalInventoryManager chemicalInventoryManager,
                           WaterQualityForecasterManager forecasterManager) {
        this.wqService = wqService;
        this.costRepo = costRepo;
        this.eventPublisher = eventPublisher;
        this.waterSourceOptimizer = waterSourceOptimizer;
        this.membraneMonitor = membraneMonitor;
        this.chemicalInventoryManager = chemicalInventoryManager;
        this.forecasterManager = forecasterManager;
    }

    @Scheduled(fixedRate = 7200000)
    public void triggerModelRetrain() {
        log.info("Scheduled model retrain trigger");
        eventPublisher.publishEvent(new ModelRetrainRequestEvent(this, "scheduled"));
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void calculateDailyCost() {
        log.info("Calculating daily cost indicators");
        try {
            Map<String, com.water.dosing.entity.WaterQuality> latest = wqService.getAllLatest();
            com.water.dosing.entity.WaterQuality raw = latest.get("raw_water");
            if (raw != null && raw.getTurbidity() != null && raw.getFlowRate() != null) {
                double turb = raw.getTurbidity();
                double flow = raw.getFlowRate();
                double alum = (2.5 + turb * 0.15) * flow * 24 / 1000;
                double chlorine = 1.5 * flow * 24 / 1000;
                double electricity = 0.22 * flow * 24;

                CostIndicator cost = new CostIndicator();
                cost.setTime(Instant.now());
                cost.setAlumConsumption(Math.round(alum * 100.0) / 100.0);
                cost.setChlorineConsumption(Math.round(chlorine * 100.0) / 100.0);
                cost.setElectricityConsumption(Math.round(electricity * 100.0) / 100.0);
                costRepo.save(cost);
            }
        } catch (Exception e) {
            log.error("Daily cost calculation failed", e);
        }
    }

    @Scheduled(cron = "0 0 6 * * ?")
    public void optimizeWaterDistribution() {
        log.info("Scheduled water distribution optimization");
        try {
            Map<String, Object> result = waterSourceOptimizer.runOptimization();
            log.info("Water distribution optimization complete: cost={}, feasible={}",
                    result.get("totalCost"), result.get("feasible"));
        } catch (Exception e) {
            log.error("Water distribution optimization failed", e);
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void evaluateMembraneFouling() {
        log.info("Scheduled membrane fouling evaluation");
        try {
            Map<String, Object> result = membraneMonitor.evaluateFoulingAndPredictCleaning();
            log.info("Membrane fouling evaluation complete: urgent={}, soon={}",
                    result.get("urgentCount"), result.get("soonCount"));
        } catch (Exception e) {
            log.error("Membrane fouling evaluation failed", e);
        }
    }

    @Scheduled(cron = "0 0 7 * * ?")
    public void updateChemicalInventory() {
        log.info("Scheduled chemical inventory update");
        try {
            com.water.dosing.module.chemical.dto.InventoryUpdateResult result =
                    chemicalInventoryManager.updateInventoryAndCheckReorder();
            log.info("Chemical inventory update complete: reorder={}, newRequisitions={}",
                    result.getReorderCount(), result.getNewRequisitions());
        } catch (Exception e) {
            log.error("Chemical inventory update failed", e);
        }
    }

    @Scheduled(fixedRate = 1800000)
    public void forecastWaterQuality() {
        log.info("Scheduled water quality forecasting (async)");
        try {
            forecasterManager.forecastAsync("outlet");
            log.info("Water quality forecast async task submitted");
        } catch (Exception e) {
            log.error("Water quality forecasting failed", e);
        }
    }
}
