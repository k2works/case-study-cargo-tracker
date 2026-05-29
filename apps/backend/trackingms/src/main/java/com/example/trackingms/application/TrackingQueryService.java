package com.example.trackingms.application;

import com.example.trackingms.domain.projections.TrackingEvent;
import com.example.trackingms.domain.projections.TrackingExceptionView;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingEventMapper;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingExceptionMapper;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 追跡 Read Model 読み取り（US17 / IT5 2.3、US19 / US20 / IT6 2.3）。
 */
@Service
public class TrackingQueryService {

    private final TrackingSummaryMapper summaryMapper;
    private final TrackingEventMapper eventMapper;
    private final TrackingExceptionMapper exceptionMapper;

    public TrackingQueryService(TrackingSummaryMapper summaryMapper,
                                TrackingEventMapper eventMapper,
                                TrackingExceptionMapper exceptionMapper) {
        this.summaryMapper = summaryMapper;
        this.eventMapper = eventMapper;
        this.exceptionMapper = exceptionMapper;
    }

    public TrackingSummary findByTrackingNumber(String trackingNumber) {
        return summaryMapper.findByTrackingNumber(trackingNumber);
    }

    public List<TrackingEvent> findEvents(String trackingNumber) {
        return eventMapper.findByTrackingNumber(trackingNumber);
    }

    public List<TrackingSummary> findAll(int offset, int limit) {
        return summaryMapper.findAll(offset, limit);
    }

    public long count() {
        return summaryMapper.count();
    }

    // --- IT6 タスク 2.3：US19 / US20 例外照会 ---

    public List<TrackingExceptionView> findExceptionsByTrackingNumber(String trackingNumber) {
        return exceptionMapper.findByTrackingNumber(trackingNumber);
    }

    public TrackingExceptionView findExceptionById(String exceptionId) {
        return exceptionMapper.findById(exceptionId);
    }

    public List<TrackingExceptionView> findAllExceptions(String responseStatus,
                                                          int offset, int limit) {
        return exceptionMapper.findAll(responseStatus, offset, limit);
    }

    public long countExceptions(String responseStatus) {
        return exceptionMapper.count(responseStatus);
    }
}
