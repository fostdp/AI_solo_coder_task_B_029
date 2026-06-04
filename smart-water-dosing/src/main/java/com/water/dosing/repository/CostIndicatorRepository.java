package com.water.dosing.repository;

import com.water.dosing.entity.CostIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CostIndicatorRepository extends JpaRepository<CostIndicator, Long> {

    List<CostIndicator> findByTimeBetweenOrderByTimeAsc(Instant start, Instant end);

    @Query(value = "SELECT * FROM cost_indicator WHERE time >= :start ORDER BY time ASC", nativeQuery = true)
    List<CostIndicator> findRecentCosts(@Param("start") Instant start);
}
