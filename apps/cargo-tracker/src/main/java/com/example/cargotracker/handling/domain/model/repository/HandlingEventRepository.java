package com.example.cargotracker.handling.domain.model.repository;

import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;

import java.util.List;
import java.util.UUID;

/**
 * 荷役イベントリポジトリインターフェース。
 */
public interface HandlingEventRepository {

    void save(HandlingEvent handlingEvent);

    List<HandlingEvent> findByBookingId(UUID bookingId);

    List<HandlingEvent> findFiltered(UUID bookingId, HandlingEventType eventType, String locationCode);

    List<HandlingEvent> findAll(int limit);
}
