package com.water.dosing.repository;

import com.water.dosing.entity.AlertRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, Integer> {

    List<AlertRecord> findByTimeBetweenOrderByTimeDesc(Instant start, Instant end);

    @Query("SELECT a FROM AlertRecord a WHERE a.acknowledged = false ORDER BY a.time DESC")
    List<AlertRecord> findUnacknowledged();

    List<AlertRecord> findTop50ByOrderByTimeDesc();
}
