package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.booking.infrastructure.repositories.BookingQueryMapper;
import com.example.cargotracker.routing.application.internal.outboundservices.acl.RoutableBookings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link RoutableBookings} の実装（ACL のアダプタ）。
 *
 * <p>返すのは素の値だけである。<strong>Routing の値オブジェクトをここで組み立てない。</strong>
 * 組み立てると Booking が Routing のドメインを直接参照することになり、
 * ACL を置いた意味が無くなる（ArchUnit ルール 4）。
 */
@Component
public class RoutableBookingsAdapter implements RoutableBookings {

    private final BookingQueryMapper mapper;

    public RoutableBookingsAdapter(BookingQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RoutableBooking> find(UUID bookingId) {
        var row = mapper.findRoutable(bookingId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new RoutableBooking(
                bookingId,
                row.getOriginUnlocode(),
                row.getDestinationUnlocode(),
                row.getArrivalDeadline(),
                row.getCargoType(),
                row.getWeight(),
                row.getShipperName()));
    }
}
