package com.example.cargotracker.tracking.application;

import com.example.cargotracker.booking.application.internal.commandservices.ConfirmBookingCommandService;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.infrastructure.repositories.ShipperMapper;
import com.example.cargotracker.shipper.infrastructure.repositories.ShipperRecord;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import com.example.cargotracker.tracking.domain.repository.TrackingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("TrackingNumberIssueService AFTER_COMMIT 統合テスト")
class TrackingNumberIssueAfterCommitIntegrationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private ConfirmBookingCommandService confirmBookingCommandService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TrackingRepository trackingRepository;

    @Autowired
    private ShipperMapper shipperMapper;

    @Test
    @Commit
    @DisplayName("予約確定の commit 後に追跡番号が発行される")
    void issueTrackingNumberAfterCommit() {
        ShipperId shipperId = createShipper();
        Booking booking = routeAssignedBooking(shipperId);
        bookingRepository.save(booking);

        confirmBookingCommandService.execute(new ConfirmBookingCommand(booking.getId().value()));

        assertThat(trackingRepository.findByBookingId(booking.getId().value())).isEmpty();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        var trackingEntry = trackingRepository.findByBookingId(booking.getId().value());
        assertThat(trackingEntry).isPresent();
        assertThat(trackingEntry.get().getTrackingNumber().value()).matches("TRK-[A-Z0-9]{8}");
    }

    private ShipperId createShipper() {
        ShipperId shipperId = ShipperId.generate();
        shipperMapper.insert(new ShipperRecord(
                shipperId.value(),
                "テスト荷主",
                "test-" + shipperId.value() + "@example.com",
                null,
                null,
                "INDIVIDUAL",
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        ));
        return shipperId;
    }

    private Booking routeAssignedBooking(ShipperId shipperId) {
        return Booking.reconstitute(
                BookingId.generate(),
                shipperId,
                new CargoSpecification(CargoType.GENERAL_CARGO, new BigDecimal("100"), null, null, null, 1, null),
                new TransportCondition("JPTYO", "USNYC", LocalDate.now().plusDays(7), LocalDate.now().plusDays(30)),
                BookingStatus.PROVISIONAL,
                new AssignedRoute("VOY-001", "JPTYO/USNYC", LocalDate.now().plusDays(30))
        );
    }
}
