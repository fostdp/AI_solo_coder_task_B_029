package com.water.dosing.service;

import com.water.dosing.entity.DosingRecord;
import com.water.dosing.repository.DosingRecordRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DosingRecordService {

    private final DosingRecordRepository repository;

    public DosingRecordService(DosingRecordRepository repository) {
        this.repository = repository;
    }

    public DosingRecord save(DosingRecord record) {
        return repository.save(record);
    }

    public List<DosingRecord> get24HourHistory(String stage) {
        Instant start = Instant.now().minus(24, ChronoUnit.HOURS);
        return repository.findHistoryByStage(stage, start);
    }

    public List<DosingRecord> get72HourHistory(String stage) {
        Instant start = Instant.now().minus(72, ChronoUnit.HOURS);
        return repository.findHistoryByStage(stage, start);
    }
}
