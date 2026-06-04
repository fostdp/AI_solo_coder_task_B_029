package com.water.dosing.service;

import com.water.dosing.entity.ChemicalConsumption;
import com.water.dosing.entity.ChemicalInventory;
import com.water.dosing.entity.PurchaseRequisition;
import com.water.dosing.module.chemical.ChemicalInventoryManager;
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
class ChemicalInventoryServiceTest {

    @Mock
    private ChemicalInventoryRepository inventoryRepo;

    @Mock
    private ChemicalConsumptionRepository consumptionRepo;

    @Mock
    private PurchaseRequisitionRepository requisitionRepo;

    @Mock
    private ChemicalInventoryManager moduleManager;

    private ChemicalInventoryService service;

    @BeforeEach
    void setUp() {
        service = new ChemicalInventoryService(inventoryRepo, consumptionRepo, requisitionRepo, moduleManager);
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

    private InventoryUpdateResult createUpdateResult(int reorderCount, int newRequisitions, int totalChemicals) {
        InventoryUpdateResult result = new InventoryUpdateResult();
        result.setUpdateId("INV-UPDATE-TEST");
        result.setUpdateTime(Instant.now());
        result.setReorderCount(reorderCount);
        result.setNewRequisitions(newRequisitions);
        result.setTotalChemicals(totalChemicals);
        return result;
    }

    @Nested
    @DisplayName("委托模式回归测试")
    class DelegationTests {

        @Test
        @DisplayName("getAllInventoryStatus应委托给模块")
        void getAllInventoryStatus_shouldDelegateToModule() {
            Map<String, Object> moduleResult = new LinkedHashMap<>();
            moduleResult.put("totalCount", 3);
            moduleResult.put("lowStockCount", 1);
            moduleResult.put("criticalCount", 1);
            moduleResult.put("needAttentionCount", 2);
            when(moduleManager.getAllInventoryStatus()).thenReturn(moduleResult);

            Map<String, Object> result = service.getAllInventoryStatus();

            assertThat(result.get("totalCount")).isEqualTo(3);
            verify(moduleManager).getAllInventoryStatus();
        }

        @Test
        @DisplayName("get7DayDemandForecast应委托给模块")
        void get7DayDemandForecast_shouldDelegateToModule() {
            List<Map<String, Object>> forecast = new ArrayList<>();
            when(moduleManager.get7DayDemandForecast("PAC")).thenReturn(forecast);

            List<Map<String, Object>> result = service.get7DayDemandForecast("PAC");

            verify(moduleManager).get7DayDemandForecast("PAC");
        }

        @Test
        @DisplayName("updateInventoryAndCheckReorder应委托给模块并转换结果")
        void updateInventoryAndCheckReorder_shouldDelegateAndConvert() {
            InventoryUpdateResult moduleResult = createUpdateResult(1, 1, 5);
            when(moduleManager.updateInventoryAndCheckReorder()).thenReturn(moduleResult);

            Map<String, Object> result = service.updateInventoryAndCheckReorder();

            assertThat(result.get("reorderCount")).isEqualTo(1);
            assertThat(result.get("newRequisitions")).isEqualTo(1);
            assertThat(result.get("totalChemicals")).isEqualTo(5);
            assertThat(result.get("delegatedTo")).isEqualTo("ChemicalInventoryManager");
            verify(moduleManager).updateInventoryAndCheckReorder();
        }

        @Test
        @DisplayName("库存正常时更新结果应显示reorderCount为0")
        void updateInventory_normalStock_shouldShowZeroReorders() {
            InventoryUpdateResult moduleResult = createUpdateResult(0, 0, 5);
            when(moduleManager.updateInventoryAndCheckReorder()).thenReturn(moduleResult);

            Map<String, Object> result = service.updateInventoryAndCheckReorder();

            assertThat(result.get("reorderCount")).isEqualTo(0);
            assertThat(result.get("newRequisitions")).isEqualTo(0);
        }

        @Test
        @DisplayName("approveRequisition应委托给模块")
        void approveRequisition_shouldDelegateToModule() {
            PurchaseRequisition req = new PurchaseRequisition("PR-001", Instant.now(), "PAC");
            req.setStatus("approved");
            req.setApprovedBy("admin");
            req.setApprovedTime(Instant.now());
            when(moduleManager.approveRequisition("PR-001", "admin")).thenReturn(req);

            PurchaseRequisition result = service.approveRequisition("PR-001", "admin");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("approved");
            verify(moduleManager).approveRequisition("PR-001", "admin");
        }

        @Test
        @DisplayName("approveRequisition不存在时应返回null")
        void approveRequisition_nonExistent_shouldReturnNull() {
            when(moduleManager.approveRequisition("PR-999", "admin")).thenReturn(null);

            PurchaseRequisition result = service.approveRequisition("PR-999", "admin");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("completeRequisition应委托给模块")
        void completeRequisition_shouldDelegateToModule() {
            PurchaseRequisition completed = new PurchaseRequisition("PR-001", Instant.now(), "PAC");
            completed.setStatus("completed");
            when(moduleManager.completeRequisition("PR-001", 3000.0)).thenReturn(completed);

            PurchaseRequisition result = service.completeRequisition("PR-001", 3000.0);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("completed");
            verify(moduleManager).completeRequisition("PR-001", 3000.0);
        }

        @Test
        @DisplayName("getLowStockAlerts应委托给模块")
        void getLowStockAlerts_shouldDelegateToModule() {
            Map<String, Object> alertsResult = new LinkedHashMap<>();
            alertsResult.put("totalAlerts", 0);
            when(moduleManager.getLowStockAlerts()).thenReturn(alertsResult);

            Map<String, Object> result = service.getLowStockAlerts();

            assertThat(result.get("totalAlerts")).isEqualTo(0);
            verify(moduleManager).getLowStockAlerts();
        }

        @Test
        @DisplayName("calculateSupplierReliability应委托给模块")
        void calculateSupplierReliability_shouldDelegateToModule() {
            Map<String, Object> reliabilityResult = new LinkedHashMap<>();
            reliabilityResult.put("reliabilityScore", 0.85);
            when(moduleManager.calculateSupplierReliability("供应商A")).thenReturn(reliabilityResult);

            Map<String, Object> result = service.calculateSupplierReliability("供应商A");

            assertThat(result.get("reliabilityScore")).isEqualTo(0.85);
            verify(moduleManager).calculateSupplierReliability("供应商A");
        }
    }

    @Nested
    @DisplayName("库存状态分类逻辑测试")
    class StockClassificationTests {

        @Test
        @DisplayName("库存状态分类应正确判定")
        void stockStatus_shouldClassifyCorrectly() {
            assertThat(classifyStockStatus(0, 1000, 2000)).isEqualTo("out_of_stock");
            assertThat(classifyStockStatus(500, 1000, 2000)).isEqualTo("critical");
            assertThat(classifyStockStatus(1500, 1000, 2000)).isEqualTo("low");
            assertThat(classifyStockStatus(3000, 1000, 2000)).isEqualTo("normal");
        }

        private String classifyStockStatus(double currentStock, double safetyStock, double reorderPoint) {
            if (currentStock <= 0) return "out_of_stock";
            if (currentStock < safetyStock) return "critical";
            if (currentStock < reorderPoint) return "low";
            return "normal";
        }
    }
}
