package com.example.cargotracker.booking.infrastructure;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingLeg;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("BookingRepository 統合テスト")
class BookingRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ShipperMapper shipperMapper;

    private ShipperId createShipper() {
        ShipperId shipperId = ShipperId.generate();
        ShipperRecord row = new ShipperRecord(
                shipperId.value(),
                "テスト荷主", "test-" + shipperId.value() + "@example.com", null, null,
                "INDIVIDUAL", null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
        shipperMapper.insert(row);
        return shipperId;
    }

    private CargoSpecification anyCargo() {
        return new CargoSpecification(
                CargoType.GENERAL_CARGO,
                new BigDecimal("100.00"),
                new BigDecimal("120.00"),
                new BigDecimal("80.00"),
                new BigDecimal("60.00"),
                2, "テスト貨物");
    }

    private TransportCondition anyTransport() {
        return new TransportCondition(
                "JPTYO", "USNYC",
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 9, 1));
    }

    @Test
    @DisplayName("予約を保存して ID で取得できる")
    void saveAndFindById() {
        ShipperId shipperId = createShipper();
        BookingId bookingId = BookingId.generate();
        Booking booking = Booking.register(bookingId, shipperId, anyCargo(), anyTransport());

        bookingRepository.save(booking);

        Optional<Booking> found = bookingRepository.findById(bookingId);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(bookingId);
        assertThat(found.get().getShipperId()).isEqualTo(shipperId);
        assertThat(found.get().getStatus()).isEqualTo(BookingStatus.PROVISIONAL);
        assertThat(found.get().getCargoSpecification().cargoType()).isEqualTo(CargoType.GENERAL_CARGO);
        assertThat(found.get().getCargoSpecification().weightKg()).isEqualByComparingTo("100.00");
        assertThat(found.get().getTransportCondition().originLocation()).isEqualTo("JPTYO");
        assertThat(found.get().getTransportCondition().destinationLocation()).isEqualTo("USNYC");
    }

    @Test
    @DisplayName("存在しない ID の場合は空の Optional を返す")
    void findByIdNotFound() {
        Optional<Booking> found = bookingRepository.findById(BookingId.generate());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("取得した予約はドメインイベントを持たない")
    void findByIdShouldNotContainDomainEvents() {
        ShipperId shipperId = createShipper();
        Booking booking = Booking.register(BookingId.generate(), shipperId, anyCargo(), anyTransport());
        bookingRepository.save(booking);

        Booking found = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(found.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("保存済みの予約一覧を取得できる")
    void findAll() {
        ShipperId firstShipperId = createShipper();
        ShipperId secondShipperId = createShipper();
        Booking first = Booking.register(BookingId.generate(), firstShipperId, anyCargo(), anyTransport());
        Booking second = Booking.register(BookingId.generate(), secondShipperId, anyCargo(), anyTransport());

        bookingRepository.save(first);
        bookingRepository.save(second);

        List<Booking> found = bookingRepository.findAll();

        assertThat(found)
                .extracting(booking -> booking.getId().value())
                .contains(first.getId().value(), second.getId().value());
    }

    @Test
    @DisplayName("区間詳細を含む予約を保存・取得できる")
    void saveAndFindByIdWithLegs() {
        ShipperId shipperId = createShipper();
        BookingId bookingId = BookingId.generate();
        Booking booking = Booking.register(bookingId, shipperId, anyCargo(), anyTransport());
        AssignedRoute route = new AssignedRoute("VOY-001", "JPTYO/SGSIN/USNYC", LocalDate.of(2026, 9, 15));
        List<BookingLeg> legs = List.of(
                new BookingLeg("VOY-001", "JPTYO", "SGSIN",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 0),
                new BookingLeg("VOY-001", "SGSIN", "USNYC",
                        LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 15), 1)
        );
        booking.assignRouteWithLegs(route, legs);

        bookingRepository.save(booking);

        Booking found = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(found.getAssignedRoute()).isEqualTo(route);
        assertThat(found.getLegs()).hasSize(2);
        assertThat(found.getLegs())
                .extracting(BookingLeg::originLocode)
                .containsExactly("JPTYO", "SGSIN");
        assertThat(found.getLegs())
                .extracting(BookingLeg::legOrder)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("区間詳細を含む予約の legs は legOrder 順で返る")
    void legsReturnedInOrder() {
        ShipperId shipperId = createShipper();
        BookingId bookingId = BookingId.generate();
        Booking booking = Booking.register(bookingId, shipperId, anyCargo(), anyTransport());
        AssignedRoute route = new AssignedRoute("VOY-002", "JPTYO/SGSIN/USNYC", LocalDate.of(2026, 9, 15));
        List<BookingLeg> legs = List.of(
                new BookingLeg("VOY-002", "SGSIN", "USNYC",
                        LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 15), 1),
                new BookingLeg("VOY-002", "JPTYO", "SGSIN",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 0)
        );
        booking.assignRouteWithLegs(route, legs);
        bookingRepository.save(booking);

        Booking found = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(found.getLegs())
                .extracting(BookingLeg::originLocode)
                .containsExactly("JPTYO", "SGSIN");
    }
}
