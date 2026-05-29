package com.example.handlingms.application;

import com.example.handlingms.domain.projections.HandlingActivitySummary;
import com.example.handlingms.infrastructure.repositories.mybatis.HandlingActivityMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 荷役作業 Read Model 読み取り（US15・US16 / IT5 3.x）。
 */
@Service
public class HandlingActivityQueryService {

    private final HandlingActivityMapper mapper;

    public HandlingActivityQueryService(HandlingActivityMapper mapper) {
        this.mapper = mapper;
    }

    public HandlingActivitySummary findById(String activityId) {
        return mapper.findById(activityId);
    }

    public List<HandlingActivitySummary> findByTrackingNumber(String trackingNumber) {
        return mapper.findByTrackingNumber(trackingNumber);
    }

    public List<HandlingActivitySummary> findAll(int offset, int limit) {
        return mapper.findAll(offset, limit);
    }

    public long count() {
        return mapper.count();
    }
}
