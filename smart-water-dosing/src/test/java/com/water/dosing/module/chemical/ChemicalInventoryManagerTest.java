package com.water.dosing.module.chemical;

import com.water.dosing.entity.ChemicalConsumption;
import com.water.dosing.entity.ChemicalInventory;
import com.water.dosing.entity.PurchaseRequisition;
import com.water.dosing.module.chemical.dto.InventoryUpdateResult;
import com.water.dosing.repository.ChemicalConsumptionRepository;
import com.water.dosing.repository.ChemicalInventoryRepository;
import com.water.dosing.repository.PurchaseRequisitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChemicalInventoryManagerTest {

    @Mock
    private ChemicalInventoryRepository inventoryRepo;

    @Mock
    private ChemicalConsumptionRepository consumptionRepo;

    @Mock
    private PurchaseRequisitionRepository requisitionRepo;

    private ChemicalInventoryConfig config;
    private ChemicalInventoryManager module;

    @BeforeEach
    void setUp() {
        config = new ChemicalInventoryConfig();
        config.setPurchaseLeadTimeDays(3);
        config.setSecurityDays(7);
        config.setAutoCreateRequisition(true);
        config.setDynamicSafetyStockEnabled(false);
        config.setReliabilityHistoryDays(90);
        config.setMinReliabilityScore(0.5);
        module = new ChemicalInventoryManager(inventoryRepo, consumptionRepo, requisitionRepo, config);
    }

    private ChemicalInventory createInventory(String code, String name, String type,
                                               double currentStock, double safetyStock,
                                               double reorderPoint, double maxStock,
                                               double dailyConsumption, String status) {
        ChemicalInventory inv = new ChemicalInventory(Instant.now(), code, name, type);
        inv.setCurrentStock(currentStock);
        inv.setSafetyStock(safetyStock);
        inv.setReorderPoint(reorderPoint);
        inv.setMaxStock(maxStock);
        inv.setDailyConsumption(dailyConsumption);
        inv.setConsumption7dAvg(dailyConsumption * 0.95);
        inv.setConsumption30dAvg(dailyConsumption);
        inv.setStockUnit("kg");
        inv.setStockStatus(status);
        inv.setUnitPrice(1.5);
        inv.setSupplier("测试供应商");
        inv.setIsActive(true);
        return inv;
    }

    private List<ChemicalConsumption> createConsumptionHistory(String code, double dailyQty, int days) {
        List<ChemicalConsumption> list = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = days; i > 0; i--) {
            ChemicalConsumption c = new ChemicalConsumption(now.minus(i, ChronoUnit.DAYS), code, code);
            c.setConsumptionQty(dailyQty + (Math.random() - 0.5) * dailyQty * 0.2);
            c.setIsPredicted(false);
            list.add(c);
        }
        return list;
    }

    @Nested
    @DisplayName("模块信息测试")
    class ModuleInfoTests {

        @Test
        @DisplayName("getModuleInfo应返回正确的模块名称和版本")
        void getModuleInfo_shouldReturnCorrectInfo() {
            when(inventoryRepo.findAllActive()).thenReturn(Collections.emptyList());

            Map<String, Object> info = module.getModuleInfo();

            assertThat(info.get("moduleName")).isEqualTo("ChemicalInventoryManager");
            assertThat(info.get("version")).isEqualTo("1.0.0");
            assertThat(info.get("status")).isEqualTo("active");
        }
    }

    @Nested
    @DisplayName("库存状态查询测试")
    class InventoryStatusTests {

        @Test
        @DisplayName("getAllInventoryStatus应正确统计各状态数量")
        void getAllInventoryStatus_shouldCountCorrectly() {
            ChemicalInventory normal = createInventory("PAC", "PAC", "coagulant",
                    6000, 1500, 2500, 8000, 250, "normal");
            ChemicalInventory low = createInventory("PAM", "PAM", "coagulant_aid",
                    400, 300, 500, 1500, 45, "low");
            ChemicalInventory critical = createInventory("NaOCl", "NaOCl", "disinfectant",
                    200, 1000, 1800, 5000, 180, "critical");

            when(inventoryRepo.findAllActive()).thenReturn(Arrays.asList(normal, low, critical));

            Map<String, Object> result = module.getAllInventoryStatus();

            assertThat(result.get("totalCount")).isEqualTo(3);
            assertThat(result.get("lowStockCount")).isEqualTo(1);
            assertThat(result.get("criticalCount")).isEqualTo(1);
            assertThat(result.get("needAttentionCount")).isEqualTo(2);
            assertThat(result.get("module")).isEqualTo("ChemicalInventoryManager");
        }

        @Test
        @DisplayName("getInventoryLatest应返回最新库存记录")
        void getInventoryLatest_shouldReturnLatestRecord() {
            ChemicalInventory inv = createInventory("PAC", "聚合氯化铝", "coagulant",
                    4500, 1500, 2500, 8000, 250, "normal");

            when(inventoryRepo.findLatestByChemicalCode("PAC")).thenReturn(Collections.singletonList(inv));

            ChemicalInventory result = module.getInventoryLatest("PAC");

            assertThat(result).isNotNull();
            assertThat(result.getChemicalCode()).isEqualTo("PAC");
        }
    }

    @Nested
    @DisplayName("消耗量预测测试")
    class DemandForecastTests {

        @Test
        @DisplayName("7天需求预测应基于日均消耗量")
        void get7DayDemandForecast_shouldBeBasedOnDailyConsumption() {
            ChemicalInventory inv = createInventory("PAC", "聚合氯化铝", "coagulant",
                    4500, 1500, 2500, 8000, 250, "normal");

            when(inventoryRepo.findLatestByChemicalCode("PAC")).thenReturn(Collections.singletonList(inv));

            List<Map<String, Object>> forecast = module.get7DayDemandForecast("PAC");

            assertThat(forecast).hasSize(7);
        }

        @Test
        @DisplayName("不存在的化学品应返回空列表")
        void get7DayDemandForecast_nonExistentChemical_shouldReturnEmpty() {
            when(inventoryRepo.findLatestByChemicalCode("XXX")).thenReturn(Collections.emptyList());

            List<Map<String, Object>> forecast = module.get7DayDemandForecast("XXX");

            assertThat(forecast).isEmpty();
        }
    }

    @Nested
    @DisplayName("库存更新和采购触发测试")
    class InventoryUpdateTests {

        @Test
        @DisplayName("库存低于再订货点时应触发采购")
        void updateInventory_belowReorderPoint_shouldTriggerRequisition() {
            ChemicalInventory inv = createInventory("PAC", "聚合氯化铝", "coagulant",
                    2000, 1500, 2500, 8000, 250, "low");

            when(inventoryRepo.findAllActiveChemicalCodes()).thenReturn(Collections.singletonList("PAC"));
            when(inventoryRepo.findLatestByChemicalCode("PAC")).thenReturn(Collections.singletonList(inv));
            when(consumptionRepo.findActualConsumption(eq("PAC"), any(Instant.class)))
                    .thenReturn(createConsumptionHistory("PAC", 250, 7));
            when(requisitionRepo.findPendingByChemicalCode("PAC", "pending"))
                    .thenReturn(Collections.emptyList());
            when(inventoryRepo.save(any(ChemicalInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(requisitionRepo.save(any(PurchaseRequisition.class))).thenAnswer(invocation -> invocation.getArgument(0));

            InventoryUpdateResult result = module.updateInventoryAndCheckReorder();

            assertThat(result.getReorderCount()).isEqualTo(1);
            assertThat(result.getNewRequisitions()).isEqualTo(1);
        }

        @Test
        @DisplayName("库存正常时不应触发采购")
        void updateInventory_normalStock_shouldNotTriggerRequisition() {
            ChemicalInventory inv = createInventory("PAC", "聚合氯化铝", "coagulant",
                    6000, 1500, 2500, 8000, 250, "normal");

            when(inventoryRepo.findAllActiveChemicalCodes()).thenReturn(Collections.singletonList("PAC"));
            when(inventoryRepo.findLatestByChemicalCode("PAC")).thenReturn(Collections.singletonList(inv));
            when(consumptionRepo.findActualConsumption(eq("PAC"), any(Instant.class)))
                    .thenReturn(createConsumptionHistory("PAC", 250, 7));
            when(inventoryRepo.save(any(ChemicalInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            InventoryUpdateResult result = module.updateInventoryAndCheckReorder();

            assertThat(result.getReorderCount()).isEqualTo(0);
            assertThat(result.getNewRequisitions()).isEqualTo(0);
        }

        @Test
        @DisplayName("已有待审批采购单时不应重复生成")
        void updateInventory_existingPending_shouldNotCreateDuplicate() {
            ChemicalInventory inv = createInventory("PAC", "聚合氯化铝", "coagulant",
                    2000, 1500, 2500, 8000, 250, "low");

            PurchaseRequisition existing = new PurchaseRequisition("PR-001", Instant.now(), "PAC");
            existing.setStatus("pending");

            when(inventoryRepo.findAllActiveChemicalCodes()).thenReturn(Collections.singletonList("PAC"));
            when(inventoryRepo.findLatestByChemicalCode("PAC")).thenReturn(Collections.singletonList(inv));
            when(consumptionRepo.findActualConsumption(eq("PAC"), any(Instant.class)))
                    .thenReturn(createConsumptionHistory("PAC", 250, 7));
            when(requisitionRepo.findPendingByChemicalCode("PAC", "pending"))
                    .thenReturn(Collections.singletonList(existing));
            when(inventoryRepo.save(any(ChemicalInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            InventoryUpdateResult result = module.updateInventoryAndCheckReorder();

            assertThat(result.getReorderCount()).isEqualTo(1);
            assertThat(result.getNewRequisitions()).isEqualTo(0);
        }

        @Test
        @DisplayName("关闭自动采购时不生成采购申请")
        void updateInventory_autoCreateDisabled_shouldNotCreateRequisition() {
            config.setAutoCreateRequisition(false);

            ChemicalInventory inv = createInventory("PAC", "聚合氯化铝", "coagulant",
                    2000, 1500, 2500, 8000, 250, "low");

            when(inventoryRepo.findAllActiveChemicalCodes()).thenReturn(Collections.singletonList("PAC"));
            when(inventoryRepo.findLatestByChemicalCode("PAC")).thenReturn(Collections.singletonList(inv));
            when(consumptionRepo.findActualConsumption(eq("PAC"), any(Instant.class)))
                    .thenReturn(createConsumptionHistory("PAC", 250, 7));
            when(inventoryRepo.save(any(ChemicalInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            InventoryUpdateResult result = module.updateInventoryAndCheckReorder();

            assertThat(result.getNewRequisitions()).isEqualTo(0);
            verify(requisitionRepo, never()).save(any(PurchaseRequisition.class));
        }
    }

    @Nested
    @DisplayName("采购申请审批和完成测试")
    class RequisitionTests {

        @Test
        @DisplayName("审批采购申请应设置审批人和审批时间")
        void approveRequisition_shouldSetApproverAndTime() {
            PurchaseRequisition req = new PurchaseRequisition("PR-001", Instant.now(), "PAC");
            req.setStatus("pending");

            when(requisitionRepo.findByRequisitionCode("PR-001")).thenReturn(Optional.of(req));
            when(requisitionRepo.save(any(PurchaseRequisition.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PurchaseRequisition approved = module.approveRequisition("PR-001", "admin");

            assertThat(approved).isNotNull();
            verify(requisitionRepo).save(argThat(r ->
                    r.getStatus().equals("approved") &&
                    r.getApprovedBy().equals("admin") &&
                    r.getApprovedTime() != null
            ));
        }

        @Test
        @DisplayName("不存在的采购申请应返回null")
        void approveRequisition_nonExistent_shouldReturnNull() {
            when(requisitionRepo.findByRequisitionCode("PR-999")).thenReturn(Optional.empty());

            PurchaseRequisition result = module.approveRequisition("PR-999", "admin");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("完成采购应增加库存")
        void completeRequisition_shouldIncreaseStock() {
            PurchaseRequisition req = new PurchaseRequisition("PR-001", Instant.now(), "PAC");
            req.setStatus("approved");
            req.setChemicalName("聚合氯化铝");

            ChemicalInventory current = createInventory("PAC", "聚合氯化铝", "coagulant",
                    2000, 1500, 2500, 8000, 250, "low");

            when(requisitionRepo.findByRequisitionCode("PR-001")).thenReturn(Optional.of(req));
            when(inventoryRepo.findLatestByChemicalCode("PAC")).thenReturn(Collections.singletonList(current));
            when(requisitionRepo.save(any(PurchaseRequisition.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(inventoryRepo.save(any(ChemicalInventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PurchaseRequisition completed = module.completeRequisition("PR-001", 3000.0);

            assertThat(completed).isNotNull();
            verify(inventoryRepo).save(argThat(inv ->
                    inv.getCurrentStock() == 5000.0 &&
                    inv.getStockStatus().equals("normal")
            ));
        }
    }

    @Nested
    @DisplayName("供应商可靠性测试")
    class SupplierReliabilityTests {

        @Test
        @DisplayName("计算供应商可靠性应返回可靠性评分")
        void calculateSupplierReliability_shouldReturnReliabilityScore() {
            when(requisitionRepo.findBySupplierAndStatusAndCompletedTimeBetween(
                    anyString(), eq("completed"), any(Instant.class), any(Instant.class)))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = module.calculateSupplierReliability("供应商A");

            assertThat(result).containsKey("reliabilityScore");
            assertThat(result).containsKey("avgDelayDays");
            assertThat(result).containsKey("onTimeRate");
            assertThat(result.get("module")).isEqualTo("ChemicalInventoryManager");
        }
    }

    @Nested
    @DisplayName("低库存告警测试")
    class LowStockAlertTests {

        @Test
        @DisplayName("低库存应产生告警")
        void getLowStockAlerts_lowStock_shouldReturnAlerts() {
            ChemicalInventory lowInv = createInventory("PAC", "聚合氯化铝", "coagulant",
                    2000, 1500, 2500, 8000, 250, "low");

            when(inventoryRepo.findByStockStatusInAndIsActiveTrue(Arrays.asList("low", "critical", "out_of_stock")))
                    .thenReturn(Collections.singletonList(lowInv));

            Map<String, Object> result = module.getLowStockAlerts();

            List<Map<String, Object>> alerts = (List<Map<String, Object>>) result.get("alerts");
            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).get("stockStatus")).isEqualTo("low");
            assertThat(result.get("module")).isEqualTo("ChemicalInventoryManager");
        }

        @Test
        @DisplayName("正常库存不应产生告警")
        void getLowStockAlerts_normalStock_shouldReturnNoAlerts() {
            when(inventoryRepo.findByStockStatusInAndIsActiveTrue(anyList()))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = module.getLowStockAlerts();

            List<Map<String, Object>> alerts = (List<Map<String, Object>>) result.get("alerts");
            assertThat(alerts).isEmpty();
            assertThat(result.get("totalAlerts")).isEqualTo(0);
        }
    }
}
