package com.example.cargotracker.routing;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.routing.application.internal.queryservices.BookingDataNotFoundException;
import com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("RouteSearchService 統合テスト")
class RouteSearchServiceIntegrationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private RouteSearchService routeSearchService;

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
    @DisplayName("予約 ID でルート候補を取得できる")
    void searchByBookingIdはルート候補を返す() {
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
            LocalDate.of(2025, 8, 1), LocalDate.of(2025, 12, 31)
        );
        bookingRepository.save(Booking.register(bookingId, shipperId, cargo, transport));

        // Act
        List<RouteCandidate> result = routeSearchService.searchByBookingId(bookingId.value());

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result).allMatch(c -> c.transitDays() > 0);
        assertThat(result).allMatch(c -> c.estimatedPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("存在しない予約 ID は BookingDataNotFoundException をスローする")
    void 存在しない予約IDはBookingDataNotFoundExceptionをスローする() {
        UUID nonExistentId = UUID.randomUUID();

        assertThatThrownBy(() -> routeSearchService.searchByBookingId(nonExistentId))
            .isInstanceOf(BookingDataNotFoundException.class)
            .hasMessageContaining(nonExistentId.toString());
    }
}
