package com.water.dosing.repository;

import com.water.dosing.entity.ChemicalInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ChemicalInventoryRepository extends JpaRepository<ChemicalInventory, Long> {

    @Query("SELECT c FROM ChemicalInventory c WHERE c.isActive = true ORDER BY c.chemicalCode")
    List<ChemicalInventory> findAllActive();

    List<ChemicalInventory> findByChemicalCodeOrderByTimeDesc(String chemicalCode);

    @Query("SELECT c FROM ChemicalInventory c WHERE c.chemicalCode = :chemicalCode ORDER BY c.time DESC")
    List<ChemicalInventory> findLatestByChemicalCode(@Param("chemicalCode") String chemicalCode);

    List<ChemicalInventory> findByChemicalTypeAndIsActiveTrue(String chemicalType);

    List<ChemicalInventory> findByStockStatusInAndIsActiveTrue(List<String> statuses);

    List<ChemicalInventory> findByTimeBetweenOrderByTimeAsc(Instant start, Instant end);

    @Query("SELECT DISTINCT c.chemicalCode FROM ChemicalInventory c WHERE c.isActive = true")
    List<String> findAllActiveChemicalCodes();
}
