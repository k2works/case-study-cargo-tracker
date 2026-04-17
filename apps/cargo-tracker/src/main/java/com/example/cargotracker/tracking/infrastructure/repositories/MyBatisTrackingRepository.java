package com.example.cargotracker.tracking.infrastructure.repositories;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.repository.TrackingRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisTrackingRepository implements TrackingRepository {

    private final TrackingMapper trackingMapper;

    public MyBatisTrackingRepository(TrackingMapper trackingMapper) {
        this.trackingMapper = trackingMapper;
    }

    @Override
    public void save(TrackingRecord trackingRecord) {
        com.example.cargotracker.tracking.infrastructure.repositories.TrackingRecord record = toRecord(trackingRecord);
        trackingMapper.insert(record);
    }

    @Override
    public Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber) {
        return Optional.ofNullable(trackingMapper.findByTrackingNumber(trackingNumber.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<TrackingRecord> findByBookingId(TrackingBookingId bookingId) {
        return Optional.ofNullable(trackingMapper.findByBookingId(bookingId.toString()))
                .map(this::toDomain);
    }

    private com.example.cargotracker.tracking.infrastructure.repositories.TrackingRecord toRecord(TrackingRecord domain) {
        com.example.cargotracker.tracking.infrastructure.repositories.TrackingRecord record =
                new com.example.cargotracker.tracking.infrastructure.repositories.TrackingRecord();
        record.setTrackingNumber(domain.getTrackingNumber().value());
        record.setBookingId(domain.getBookingId().toString());
        record.setCargoStatus(domain.getStatus().name());
        return record;
    }

    private TrackingRecord toDomain(com.example.cargotracker.tracking.infrastructure.repositories.TrackingRecord record) {
        return TrackingRecord.reconstruct(
                TrackingNumber.of(record.getTrackingNumber()),
                TrackingBookingId.of(record.getBookingId()),
                CargoTrackingStatus.valueOf(record.getCargoStatus())
        );
    }
}
