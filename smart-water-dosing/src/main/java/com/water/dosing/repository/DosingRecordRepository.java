package com.water.dosing.repository;

import com.water.dosing.entity.DosingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DosingRecordRepository extends JpaRepository<DosingRecord, Long> {

    List<DosingRecord> findByStageAndTimeBetweenOrderByTimeAsc(String stage, Instant start, Instant end);

    @Query(value = "SELECT * FROM dosing_record WHERE stage = :stage AND time >= :start ORDER BY time ASC", nativeQuery = true)
    List<DosingRecord> findHistoryByStage(@Param("stage") String stage, @Param("start") Instant start);
}
