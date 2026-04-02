package com.example.cargotracker.booking.infrastructure;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.application.internal.outboundservices.BookingSnapshot;
import com.example.cargotracker.routing.domain.model.CargoType;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("BookingQueryPortAdapter 統合テスト")
class BookingQueryPortAdapterTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private BookingQueryPort bookingQueryPort;

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

    @Test
    @DisplayName("予約 ID で BookingSnapshot を取得できる")
    void findByIdはBookingSnapshotを返す() {
        // Arrange
        ShipperId shipperId = createShipper();
        BookingId bookingId = BookingId.generate();
        var cargo = new CargoSpecification(
            com.example.cargotracker.booking.domain.model.valueobjects.CargoType.GENERAL_CARGO,
            new BigDecimal("500.00"),
            new BigDecimal("120.00"), new BigDecimal("80.00"), new BigDecimal("60.00"),
            1, "テスト貨物"
        );
        var transport = new TransportCondition(
            "JPTYO", "USNYC",
            LocalDate.of(2025, 8, 1), LocalDate.of(2025, 9, 30)
        );
        bookingRepository.save(Booking.register(bookingId, shipperId, cargo, transport));

        // Act
        Optional<BookingSnapshot> result = bookingQueryPort.findById(bookingId.value());

        // Assert
        assertThat(result).isPresent();
        BookingSnapshot snapshot = result.get();
        assertThat(snapshot.originLocode()).isEqualTo("JPTYO");
        assertThat(snapshot.destinationLocode()).isEqualTo("USNYC");
        assertThat(snapshot.requestedArrivalDate()).isEqualTo(LocalDate.of(2025, 9, 30));
        assertThat(snapshot.cargoType()).isEqualTo(CargoType.GENERAL);
        assertThat(snapshot.weightKg()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("DANGEROUS_GOODS は HAZARDOUS に変換される")
    void dangerousGoodsはHAZARDOUSに変換される() {
        // Arrange
        ShipperId shipperId = createShipper();
        BookingId bookingId = BookingId.generate();
        var cargo = new CargoSpecification(
            com.example.cargotracker.booking.domain.model.valueobjects.CargoType.DANGEROUS_GOODS,
            new BigDecimal("200.00"),
            new BigDecimal("100.00"), new BigDecimal("80.00"), new BigDecimal("60.00"),
            1, "危険物",
            "UN1234", null,
            null, null
        );
        var transport = new TransportCondition(
            "SGSIN", "JPTYO",
            LocalDate.of(2025, 8, 1), LocalDate.of(2025, 9, 1)
        );
        bookingRepository.save(Booking.register(bookingId, shipperId, cargo, transport));

        // Act
        Optional<BookingSnapshot> result = bookingQueryPort.findById(bookingId.value());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().cargoType()).isEqualTo(CargoType.HAZARDOUS);
    }

    @Test
    @DisplayName("REFRIGERATED は REFRIGERATED に変換される")
    void refrigeratedはREFRIGERATEDに変換される() {
        // Arrange
        ShipperId shipperId = createShipper();
        BookingId bookingId = BookingId.generate();
        var cargo = new CargoSpecification(
            com.example.cargotracker.booking.domain.model.valueobjects.CargoType.REFRIGERATED,
            new BigDecimal("300.00"),
            new BigDecimal("100.00"), new BigDecimal("80.00"), new BigDecimal("60.00"),
            1, "冷凍貨物",
            null, null,
            new BigDecimal("-18"), new BigDecimal("0")
        );
        var transport = new TransportCondition(
            "HKHKG", "JPTYO",
            LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 15)
        );
        bookingRepository.save(Booking.register(bookingId, shipperId, cargo, transport));

        // Act
        Optional<BookingSnapshot> result = bookingQueryPort.findById(bookingId.value());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().cargoType()).isEqualTo(CargoType.REFRIGERATED);
    }

    @Test
    @DisplayName("存在しない UUID は Optional.empty() を返す")
    void 存在しないUUIDはemptyを返す() {
        Optional<BookingSnapshot> result = bookingQueryPort.findById(UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}
