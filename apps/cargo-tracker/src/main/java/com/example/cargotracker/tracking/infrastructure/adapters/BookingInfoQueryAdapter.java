package com.example.cargotracker.tracking.infrastructure.adapters;

import com.example.cargotracker.tracking.application.internal.outboundservices.BookingInfoQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link BookingInfoQueryPort} の JDBC アダプター実装。
 * bookings テーブルを直接クエリして予約サマリーを返す。
 */
@Component
public class BookingInfoQueryAdapter implements BookingInfoQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public BookingInfoQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<BookingSummary> findById(UUID bookingId) {
        try {
            BookingSummary summary = jdbcTemplate.queryForObject(
                    "SELECT origin_location, destination_location, requested_delivery_date " +
                    "FROM bookings WHERE id = ?",
                    (rs, rowNum) -> new BookingSummary(
                            rs.getString("origin_location"),
                            rs.getString("destination_location"),
                            rs.getDate("requested_delivery_date").toLocalDate()
                    ),
                    bookingId
            );
            return Optional.ofNullable(summary);
        } catch (org.springframework.dao.EmptyResultDataAccessException _) {
            return Optional.empty();
        }
    }
}
