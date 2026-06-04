package com.water.dosing.repository;

import com.water.dosing.entity.MembraneCleanRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MembraneCleanRecordRepository extends JpaRepository<MembraneCleanRecord, Long> {

    List<MembraneCleanRecord> findByModuleCodeOrderByCleanTimeDesc(String moduleCode);

    List<MembraneCleanRecord> findByCleanTimeBetweenOrderByCleanTimeDesc(Instant start, Instant end);

    @Query("SELECT m FROM MembraneCleanRecord m WHERE m.moduleCode = :moduleCode ORDER BY m.cleanTime DESC")
    List<MembraneCleanRecord> findLatestByModuleCode(@Param("moduleCode") String moduleCode);

    @Query("SELECT m FROM MembraneCleanRecord m WHERE m.moduleCode = :moduleCode ORDER BY m.cleanTime DESC")
    List<MembraneCleanRecord> findLastCleanByModuleCode(@Param("moduleCode") String moduleCode);

    @Query("SELECT m.moduleCode, COUNT(m), AVG(m.fluxRecoveryPct) FROM MembraneCleanRecord m " +
           "WHERE m.cleanTime >= :start GROUP BY m.moduleCode")
    List<Object[]> getCleaningStats(@Param("start") Instant start);
}
