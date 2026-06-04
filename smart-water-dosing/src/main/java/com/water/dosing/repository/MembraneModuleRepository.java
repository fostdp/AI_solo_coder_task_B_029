package com.water.dosing.repository;

import com.water.dosing.entity.MembraneModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MembraneModuleRepository extends JpaRepository<MembraneModule, Long> {

    @Query("SELECT m FROM MembraneModule m WHERE m.isActive = true ORDER BY m.moduleCode")
    List<MembraneModule> findAllActive();

    List<MembraneModule> findByModuleCodeOrderByTimeDesc(String moduleCode);

    @Query("SELECT m FROM MembraneModule m WHERE m.moduleCode = :moduleCode ORDER BY m.time DESC")
    List<MembraneModule> findLatestByModuleCode(@Param("moduleCode") String moduleCode);

    List<MembraneModule> findByMembraneTypeAndIsActiveTrue(String membraneType);

    List<MembraneModule> findByTimeBetweenOrderByTimeAsc(Instant start, Instant end);

    @Query("SELECT m FROM MembraneModule m WHERE m.membraneType = :type AND m.time >= :start ORDER BY m.time DESC")
    List<MembraneModule> findRecentByType(@Param("type") String membraneType, @Param("start") Instant start);

    @Query("SELECT DISTINCT m.moduleCode FROM MembraneModule m WHERE m.isActive = true")
    List<String> findAllActiveModuleCodes();

    @Query("SELECT m FROM MembraneModule m WHERE m.cleanUrgency IN :urgencies AND m.isActive = true ORDER BY m.predictedCleanDays ASC")
    List<MembraneModule> findModulesNeedingCleaning(@Param("urgencies") List<String> urgencies);
}
