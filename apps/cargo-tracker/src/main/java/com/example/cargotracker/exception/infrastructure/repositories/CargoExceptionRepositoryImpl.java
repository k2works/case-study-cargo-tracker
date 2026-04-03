package com.example.cargotracker.exception.infrastructure.repositories;

import com.example.cargotracker.exception.domain.model.aggregates.CargoException;
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
    public void save(CargoException cargoException) {
        CargoExceptionRecord row = new CargoExceptionRecord(
                cargoException.getId().value().toString(),
                cargoException.getTrackingNumber(),
                cargoException.getExceptionType().name(),
                cargoException.getLocationCode(),
                cargoException.getOccurredAt(),
                cargoException.getReason(),
                cargoException.isUrgent(),
                cargoException.getResolution(),
                LocalDateTime.now()
        );
        cargoExceptionMapper.insert(row);
    }

    @Override
    public List<CargoException> findByTrackingNumber(String trackingNumber) {
        return cargoExceptionMapper.findByTrackingNumber(trackingNumber).stream()
                .map(this::toCargoException)
                .toList();
    }

    private CargoException toCargoException(CargoExceptionRecord row) {
        return CargoException.reconstitute(
                new ExceptionId(java.util.UUID.fromString(row.id())),
                row.trackingNumber(),
                ExceptionType.valueOf(row.exceptionType()),
                row.locationCode(),
                row.occurredAt(),
                row.reason(),
                Boolean.TRUE.equals(row.urgent()),
                row.resolution()
        );
    }
}
