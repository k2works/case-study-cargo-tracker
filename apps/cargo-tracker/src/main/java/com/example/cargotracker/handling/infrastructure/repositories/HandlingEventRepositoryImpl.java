package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.domain.model.repository.HandlingEventRepository;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class HandlingEventRepositoryImpl implements HandlingEventRepository {

    private final HandlingEventMapper handlingEventMapper;

    public HandlingEventRepositoryImpl(HandlingEventMapper handlingEventMapper) {
        this.handlingEventMapper = handlingEventMapper;
    }

    @Override
    public void save(HandlingEvent handlingEvent) {
        HandlingEventRecord row = new HandlingEventRecord(
                handlingEvent.getId().value(),
                handlingEvent.getBookingId(),
                handlingEvent.getEventType().name(),
                handlingEvent.getLocationCode(),
                handlingEvent.getCompletionTime(),
                handlingEvent.getMemo(),
                handlingEvent.getReceiveConfirmationCode(),
                LocalDateTime.now()
        );
        handlingEventMapper.insert(row);
    }

    @Override
    public List<HandlingEvent> findByBookingId(UUID bookingId) {
        return handlingEventMapper.findByBookingId(bookingId).stream()
                .map(this::toHandlingEvent)
                .toList();
    }

    @Override
    public List<HandlingEvent> findFiltered(UUID bookingId, HandlingEventType eventType, String locationCode) {
        String eventTypeName = eventType != null ? eventType.name() : null;
        return handlingEventMapper.findFiltered(bookingId, eventTypeName, locationCode).stream()
                .map(this::toHandlingEvent)
                .toList();
    }

    @Override
    public List<HandlingEvent> findAll(int limit) {
        return handlingEventMapper.findAll(limit).stream()
                .map(this::toHandlingEvent)
                .toList();
    }

    private HandlingEvent toHandlingEvent(HandlingEventRecord row) {
        return HandlingEvent.reconstitute(
                new HandlingEventId(row.id()),
                row.bookingId(),
                HandlingEventType.valueOf(row.eventType()),
                row.locationCode(),
                row.completionTime(),
                row.memo(),
                row.receiveConfirmationCode()
        );
    }
}
