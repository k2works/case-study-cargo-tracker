package com.example.cargotracker.handling.infrastructure;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEvent;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingEventId;
import com.example.cargotracker.handling.domain.model.repository.HandlingEventRepository;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.infrastructure.repositories.ShipperMapper;
import com.example.cargotracker.shipper.infrastructure.repositories.ShipperRecord;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("HandlingEventRepository 統合テスト")
class HandlingEventRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private HandlingEventRepository handlingEventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ShipperMapper shipperMapper;

    private BookingId createBooking() {
        ShipperId shipperId = ShipperId.generate();
        ShipperRecord shipperRow = new ShipperRecord(
                shipperId.value(),
                "テスト荷主", "test-" + shipperId.value() + "@example.com", null, null,
                "INDIVIDUAL", null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
        shipperMapper.insert(shipperRow);

        BookingId bookingId = BookingId.generate();
        Booking booking = Booking.register(
                bookingId, shipperId,
                new CargoSpecification(CargoType.GENERAL_CARGO, new BigDecimal("100.00"),
                        new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"), 1, "テスト貨物"),
                new TransportCondition("JPTYO", "USNYC",
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1))
        );
        bookingRepository.save(booking);
        return bookingId;
    }

    @Test
    @DisplayName("荷役イベントを保存して booking ID で取得できる")
    void saveAndFindByBookingId() {
        BookingId bookingId = createBooking();
        HandlingEventId eventId = HandlingEventId.generate();
        LocalDateTime completionTime = LocalDateTime.of(2026, 5, 12, 9, 0);

        HandlingEvent event = HandlingEvent.recordEvent(eventId, bookingId.value(), HandlingEventType.LOAD, "JPTYO", completionTime, null);
        handlingEventRepository.save(event);

        List<HandlingEvent> found = handlingEventRepository.findByBookingId(bookingId.value());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(eventId);
        assertThat(found.get(0).getBookingId()).isEqualTo(bookingId.value());
        assertThat(found.get(0).getEventType()).isEqualTo(HandlingEventType.LOAD);
        assertThat(found.get(0).getLocationCode()).isEqualTo("JPTYO");
        assertThat(found.get(0).getCompletionTime()).isEqualTo(completionTime);
        assertThat(found.get(0).getMemo()).isNull();
    }

    @Test
    @DisplayName("メモ付き手動更新イベントを保存・取得できる")
    void saveManualUpdateWithMemo() {
        BookingId bookingId = createBooking();
        String memo = "台風のため保管中";
        HandlingEvent event = HandlingEvent.recordEvent(HandlingEventId.generate(), bookingId.value(),
                HandlingEventType.MANUAL_UPDATE, "JPTYO", LocalDateTime.of(2026, 5, 12, 9, 0), memo);
        handlingEventRepository.save(event);

        List<HandlingEvent> found = handlingEventRepository.findByBookingId(bookingId.value());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getMemo()).isEqualTo(memo);
    }

    @Test
    @DisplayName("同一 booking に複数の荷役イベントを保存できる")
    void saveMultipleEventsForSameBooking() {
        BookingId bookingId = createBooking();
        LocalDateTime t1 = LocalDateTime.of(2026, 5, 10, 8, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 5, 12, 9, 0);

        handlingEventRepository.save(HandlingEvent.recordEvent(HandlingEventId.generate(), bookingId.value(),
                HandlingEventType.CUSTOMS, "JPTYO", t1, null));
        handlingEventRepository.save(HandlingEvent.recordEvent(HandlingEventId.generate(), bookingId.value(),
                HandlingEventType.LOAD, "JPTYO", t2, null));

        List<HandlingEvent> found = handlingEventRepository.findByBookingId(bookingId.value());
        assertThat(found).hasSize(2);
    }

    @Test
    @DisplayName("存在しない booking ID では空リストを返す")
    void findByBookingIdNotFound() {
        List<HandlingEvent> found = handlingEventRepository.findByBookingId(java.util.UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("取得した荷役イベントはドメインイベントを持たない")
    void findShouldNotContainDomainEvents() {
        BookingId bookingId = createBooking();
        handlingEventRepository.save(HandlingEvent.recordEvent(HandlingEventId.generate(), bookingId.value(),
                HandlingEventType.LOAD, "JPTYO", LocalDateTime.of(2026, 5, 12, 9, 0), null));

        HandlingEvent found = handlingEventRepository.findByBookingId(bookingId.value()).get(0);
        assertThat(found.getDomainEvents()).isEmpty();
    }
}
