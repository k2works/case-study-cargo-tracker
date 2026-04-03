package com.example.cargotracker.exception.infrastructure.adapters;

import com.example.cargotracker.exception.domain.model.repository.CargoExceptionRepository;
import com.example.cargotracker.tracking.application.internal.outboundservices.ExceptionInfoQueryPort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * tracking BC の {@link ExceptionInfoQueryPort} を exception BC の
 * {@link CargoExceptionRepository} に橋渡しするアダプター（アンチコラプションレイヤー）。
 */
@Component
public class ExceptionInfoQueryPortAdapter implements ExceptionInfoQueryPort {

    private final CargoExceptionRepository cargoExceptionRepository;

    public ExceptionInfoQueryPortAdapter(CargoExceptionRepository cargoExceptionRepository) {
        this.cargoExceptionRepository = cargoExceptionRepository;
    }

    @Override
    public List<ExceptionInfo> findByTrackingNumber(String trackingNumber) {
        return cargoExceptionRepository.findByTrackingNumber(trackingNumber)
                .stream()
                .map(incident -> new ExceptionInfo(
                        incident.getOccurredAt(),
                        incident.getLocationCode(),
                        incident.getExceptionType().name(),
                        incident.getExceptionType().getDisplayName(),
                        incident.getReason(),
                        incident.getResolution()
                ))
                .toList();
    }
}
