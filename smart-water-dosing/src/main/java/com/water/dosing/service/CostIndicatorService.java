package com.water.dosing.service;

import com.water.dosing.entity.CostIndicator;
import com.water.dosing.repository.CostIndicatorRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CostIndicatorService {

    private final CostIndicatorRepository repository;

    public CostIndicatorService(CostIndicatorRepository repository) {
        this.repository = repository;
    }

    public CostIndicator save(CostIndicator cost) {
        return repository.save(cost);
    }

    public List<CostIndicator> getRecentDays(int days) {
        Instant start = Instant.now().minus(days, ChronoUnit.DAYS);
        return repository.findRecentCosts(start);
    }
}
