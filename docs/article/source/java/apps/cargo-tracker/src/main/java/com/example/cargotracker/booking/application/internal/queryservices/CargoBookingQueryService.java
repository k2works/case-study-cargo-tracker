package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.model.repository.CargoRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CargoBookingQueryService {

    private final CargoRepository cargoRepository;

    public CargoBookingQueryService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    public Optional<Cargo> findByBookingId(String bookingId) {
        return cargoRepository.findByBookingId(new BookingId(UUID.fromString(bookingId)));
    }

    public List<Cargo> findAll() {
        return cargoRepository.findAll();
    }
}
