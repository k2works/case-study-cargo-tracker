package com.example.cargotracker.handling.application.internal.queryservices;

import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.repository.HandlingEventRepository;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 荷役イベント照会クエリサービス。
 */
@Service
@Transactional(readOnly = true)
public class FindHandlingEventsQueryService {

    private final HandlingEventRepository handlingEventRepository;

    public FindHandlingEventsQueryService(HandlingEventRepository handlingEventRepository) {
        this.handlingEventRepository = handlingEventRepository;
    }

    public List<HandlingEvent> findByBookingId(UUID bookingId) {
        return handlingEventRepository.findByBookingId(bookingId);
    }

    public List<HandlingEvent> findFiltered(UUID bookingId, HandlingEventType eventType, String locationCode) {
        return handlingEventRepository.findFiltered(bookingId, eventType, locationCode);
    }

    public List<HandlingEvent> findAll(int limit) {
        return handlingEventRepository.findAll(limit);
    }
}
