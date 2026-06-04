package com.water.dosing.repository;

import com.water.dosing.entity.WaterQualityPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WaterQualityPredictionRepository extends JpaRepository<WaterQualityPrediction, Long> {

    List<WaterQualityPrediction> findByStageAndParameterNameOrderByTargetTimeAsc(String stage, String parameterName);

    @Query("SELECT p FROM WaterQualityPrediction p WHERE p.stage = :stage AND p.parameterName = :param " +
           "AND p.targetTime >= :start ORDER BY p.targetTime ASC")
    List<WaterQualityPrediction> findRecentPredictions(@Param("stage") String stage,
                                                        @Param("param") String parameterName,
                                                        @Param("start") Instant start);

    @Query("SELECT p FROM WaterQualityPrediction p WHERE p.stage = :stage AND p.predictionTime >= :start " +
           "ORDER BY p.predictionTime DESC, p.targetTime ASC")
    List<WaterQualityPrediction> findByStageAndPredictionTimeAfter(@Param("stage") String stage,
                                                                    @Param("start") Instant start);

    List<WaterQualityPrediction> findByIsAlarmTrueOrderByPredictionTimeDesc();

    List<WaterQualityPrediction> findByIsWarningTrueOrderByPredictionTimeDesc();

    @Query("SELECT p FROM WaterQualityPrediction p WHERE (p.isAlarm = true OR p.isWarning = true) " +
           "AND p.targetTime >= :start ORDER BY p.targetTime ASC")
    List<WaterQualityPrediction> findActiveWarnings(@Param("start") Instant start);

    @Query("SELECT p FROM WaterQualityPrediction p WHERE p.stage = :stage AND p.parameterName = :param " +
           "AND p.predictionTime = (SELECT MAX(p2.predictionTime) FROM WaterQualityPrediction p2 " +
           "WHERE p2.stage = :stage AND p2.parameterName = :param) " +
           "ORDER BY p.targetTime ASC")
    List<WaterQualityPrediction> findLatestPredictions(@Param("stage") String stage,
                                                        @Param("param") String parameterName);
}
