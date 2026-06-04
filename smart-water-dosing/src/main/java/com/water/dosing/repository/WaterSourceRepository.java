package com.water.dosing.repository;

import com.water.dosing.entity.WaterSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WaterSourceRepository extends JpaRepository<WaterSource, Long> {

    @Query("SELECT w FROM WaterSource w WHERE w.isActive = true ORDER BY w.sourceCode")
    List<WaterSource> findAllActive();

    List<WaterSource> findBySourceCodeOrderByTimeDesc(String sourceCode);

    @Query("SELECT w FROM WaterSource w WHERE w.sourceCode = :sourceCode ORDER BY w.time DESC")
    List<WaterSource> findLatestBySourceCode(@Param("sourceCode") String sourceCode);

    List<WaterSource> findByTimeBetweenOrderByTimeAsc(Instant start, Instant end);

    @Query("SELECT w FROM WaterSource w WHERE w.isActive = true AND w.time >= :start ORDER BY w.time DESC")
    List<WaterSource> findRecentActiveSources(@Param("start") Instant start);
}
