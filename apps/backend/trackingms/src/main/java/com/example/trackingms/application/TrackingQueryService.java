package com.example.trackingms.application;

import com.example.trackingms.domain.projections.TrackingEvent;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingEventMapper;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 追跡 Read Model 読み取り（US17 / IT5 2.3）。
 */
@Service
public class TrackingQueryService {

    private final TrackingSummaryMapper summaryMapper;
    private final TrackingEventMapper eventMapper;

    public TrackingQueryService(TrackingSummaryMapper summaryMapper,
                                TrackingEventMapper eventMapper) {
        this.summaryMapper = summaryMapper;
        this.eventMapper = eventMapper;
    }

    public TrackingSummary findByTrackingNumber(String trackingNumber) {
        return summaryMapper.findByTrackingNumber(trackingNumber);
    }

    public List<TrackingEvent> findEvents(String trackingNumber) {
        return eventMapper.findByTrackingNumber(trackingNumber);
    }
}
