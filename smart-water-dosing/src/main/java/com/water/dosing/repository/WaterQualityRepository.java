package com.water.dosing.repository;

import com.water.dosing.entity.WaterQuality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WaterQualityRepository extends JpaRepository<WaterQuality, Long> {

    List<WaterQuality> findByStageAndTimeBetweenOrderByTimeAsc(String stage, Instant start, Instant end);

    List<WaterQuality> findByTimeBetweenOrderByTimeAsc(Instant start, Instant end);

    @Query("SELECT w FROM WaterQuality w WHERE w.stage = :stage ORDER BY w.time DESC")
    List<WaterQuality> findLatestByStage(@Param("stage") String stage);

    @Query(value = "SELECT * FROM water_quality WHERE stage = :stage AND time >= :start ORDER BY time ASC", nativeQuery = true)
    List<WaterQuality> findHistoryByStage(@Param("stage") String stage, @Param("start") Instant start);
}
