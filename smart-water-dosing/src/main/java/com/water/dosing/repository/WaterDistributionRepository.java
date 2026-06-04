package com.water.dosing.repository;

import com.water.dosing.entity.WaterDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WaterDistributionRepository extends JpaRepository<WaterDistribution, Long> {

    @Query("SELECT w FROM WaterDistribution w WHERE w.isCurrent = true ORDER BY w.allocationRatio DESC")
    List<WaterDistribution> findCurrentDistribution();

    @Query("SELECT w FROM WaterDistribution w WHERE w.optimizationId = :optId ORDER BY w.allocationRatio DESC")
    List<WaterDistribution> findByOptimizationId(@Param("optId") String optimizationId);

    List<WaterDistribution> findByTimeBetweenOrderByTimeAsc(Instant start, Instant end);

    @Query("SELECT w FROM WaterDistribution w WHERE w.time >= :start ORDER BY w.time DESC, w.allocationRatio DESC")
    List<WaterDistribution> findRecentDistributions(@Param("start") Instant start);
}
