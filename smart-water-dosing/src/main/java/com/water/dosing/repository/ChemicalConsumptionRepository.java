package com.water.dosing.repository;

import com.water.dosing.entity.ChemicalConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ChemicalConsumptionRepository extends JpaRepository<ChemicalConsumption, Long> {

    List<ChemicalConsumption> findByChemicalCodeOrderByTimeDesc(String chemicalCode);

    List<ChemicalConsumption> findByChemicalCodeAndTimeBetweenOrderByTimeAsc(String chemicalCode, Instant start, Instant end);

    List<ChemicalConsumption> findByTimeBetweenOrderByTimeAsc(Instant start, Instant end);

    @Query("SELECT c FROM ChemicalConsumption c WHERE c.chemicalCode = :code AND c.time >= :start AND c.isPredicted = false ORDER BY c.time ASC")
    List<ChemicalConsumption> findActualConsumption(@Param("code") String chemicalCode, @Param("start") Instant start);

    @Query("SELECT c.chemicalCode, AVG(c.consumptionQty), SUM(c.consumptionQty) FROM ChemicalConsumption c " +
           "WHERE c.time >= :start AND c.isPredicted = false GROUP BY c.chemicalCode")
    List<Object[]> getConsumptionStats(@Param("start") Instant start);
}
