package com.example.cargotracker.exception.infrastructure.repositories;

import com.example.cargotracker.exception.domain.model.aggregates.CargoIncident;
import com.example.cargotracker.exception.domain.model.aggregates.ExceptionId;
import com.example.cargotracker.exception.domain.model.repository.CargoExceptionRepository;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class CargoExceptionRepositoryImpl implements CargoExceptionRepository {

    private final CargoExceptionMapper cargoExceptionMapper;

    public CargoExceptionRepositoryImpl(CargoExceptionMapper cargoExceptionMapper) {
        this.cargoExceptionMapper = cargoExceptionMapper;
    }

    @Override
    public void save(CargoIncident incident) {
        CargoExceptionRecord row = new CargoExceptionRecord(
                incident.getId().value().toString(),
                incident.getTrackingNumber(),
                incident.getExceptionType().name(),
                incident.getLocationCode(),
                incident.getOccurredAt(),
                incident.getReason(),
                incident.isUrgent(),
                incident.getResolution(),
                incident.getEstimatedArrivalDate(),
                LocalDateTime.now()
        );
        cargoExceptionMapper.insert(row);
    }

    @Override
    public List<CargoIncident> findByTrackingNumber(String trackingNumber) {
        return cargoExceptionMapper.findByTrackingNumber(trackingNumber).stream()
                .map(this::toCargoException)
                .toList();
    }

    private CargoIncident toCargoException(CargoExceptionRecord row) {
        CargoIncident incident = CargoIncident.reconstitute(
                new ExceptionId(java.util.UUID.fromString(row.id())),
                row.trackingNumber(),
                ExceptionType.valueOf(row.exceptionType()),
                row.locationCode(),
                row.occurredAt(),
                row.reason(),
                row.resolution()
        );
        incident.setEstimatedArrivalDate(row.estimatedArrivalDate());
        return incident;
    }
}
