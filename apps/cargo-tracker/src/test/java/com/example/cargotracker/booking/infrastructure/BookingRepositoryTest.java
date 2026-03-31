package com.example.cargotracker.booking.infrastructure;

import com.example.cargotracker.booking.domain.*;
import com.example.cargotracker.shipper.domain.*;
import com.example.cargotracker.shipper.infrastructure.persistence.ShipperMapper;
import com.example.cargotracker.shipper.infrastructure.persistence.ShipperRecord;
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
        ShipperRecord record = new ShipperRecord(
                shipperId.value(),
                "テスト荷主", "test@example.com", null, null,
                "INDIVIDUAL", null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
        shipperMapper.insert(record);
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
}
