package com.example.cargotracker.handling.infrastructure.adapters;

import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.handling.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.handling.application.internal.outboundservices.BookingExistencePort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * handling コンテキストの {@link BookingExistencePort} を booking コンテキストの
 * {@link BookingRepository} に橋渡しするアダプター（アンチコラプションレイヤー）。
 */
@Component
public class BookingExistencePortAdapter implements BookingExistencePort {

    private final BookingRepository bookingRepository;

    public BookingExistencePortAdapter(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public void verifyExists(UUID bookingId) {
        bookingRepository.findById(new BookingId(bookingId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
    }
}
