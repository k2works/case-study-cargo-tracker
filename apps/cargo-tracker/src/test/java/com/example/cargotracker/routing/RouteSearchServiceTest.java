package com.example.cargotracker.routing;

import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.application.internal.outboundservices.BookingSnapshot;
import com.example.cargotracker.routing.application.internal.outboundservices.RouteProviderPort;
import com.example.cargotracker.routing.application.internal.queryservices.BookingDataNotFoundException;
import com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService;
import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RouteSearchService")
class RouteSearchServiceTest {

    @Mock
    private BookingQueryPort bookingQueryPort;

    @Mock
    private RouteProviderPort routeProviderPort;

    private RouteSearchService service;

    @BeforeEach
    void setUp() {
        service = new RouteSearchService(bookingQueryPort, routeProviderPort);
    }

    @Test
    @DisplayName("searchByBookingId は Booking の TransportCondition から RouteSearchQuery を組み立てて RouteProviderPort を呼ぶ")
    void searchByBookingIdはTransportConditionからRouteSearchQueryを組み立ててRouteProviderPortを呼ぶ() {
        // Arrange
        var bookingId = UUID.randomUUID();
        var arrivalDate = LocalDate.of(2025, 12, 31);
        var snapshot = new BookingSnapshot(
            "JPTYO", "USNYC", arrivalDate,
            CargoType.GENERAL, new BigDecimal("500.0")
        );
        var expectedCandidates = List.of(new RouteCandidate(
            "VOY001", List.of("SGSIN"), 14,
            new BigDecimal("1500.00"), arrivalDate,
            Set.of(CargoType.GENERAL)
        ));

        when(bookingQueryPort.findById(bookingId)).thenReturn(Optional.of(snapshot));
        when(routeProviderPort.findRoutes(any(RouteSearchQuery.class))).thenReturn(expectedCandidates);

        // Act
        var result = service.searchByBookingId(bookingId);

        // Assert
        assertThat(result).isEqualTo(expectedCandidates);
        verify(routeProviderPort).findRoutes(argThat(q ->
            q.originLocode().equals("JPTYO") &&
            q.destinationLocode().equals("USNYC") &&
            q.requestedArrivalDate().equals(arrivalDate) &&
            q.cargoType() == CargoType.GENERAL &&
            q.weightKg().compareTo(new BigDecimal("500.0")) == 0
        ));
    }

    @Test
    @DisplayName("searchByBookingId は Booking が存在しない場合に BookingNotFoundException をスローする")
    void searchByBookingIdはBookingが存在しない場合にBookingNotFoundExceptionをスローする() {
        // Arrange
        var bookingId = UUID.randomUUID();
        when(bookingQueryPort.findById(bookingId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.searchByBookingId(bookingId))
            .isInstanceOf(BookingDataNotFoundException.class)
            .hasMessageContaining(bookingId.toString());
    }

    @Test
    @DisplayName("searchByCondition は RouteProviderPort に委譲して結果を返す")
    void searchByConditionはRouteProviderPortに委譲して結果を返す() {
        // Arrange
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", LocalDate.of(2025, 12, 31),
            CargoType.REFRIGERATED, new BigDecimal("100.0")
        );
        var expectedCandidates = List.of(new RouteCandidate(
            "VOY002", List.of(), 7,
            new BigDecimal("800.00"), LocalDate.of(2025, 12, 31),
            Set.of(CargoType.REFRIGERATED)
        ));

        when(routeProviderPort.findRoutes(query)).thenReturn(expectedCandidates);

        // Act
        var result = service.searchByCondition(query);

        // Assert
        assertThat(result).isEqualTo(expectedCandidates);
        verify(routeProviderPort).findRoutes(query);
    }

    @Test
    @DisplayName("searchByCondition は希望着日を超えるルートを除外する")
    void searchByConditionは希望着日を超えるルートを除外する() {
        // Arrange
        var deadline = LocalDate.of(2025, 12, 31);
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", deadline, CargoType.GENERAL, BigDecimal.TEN
        );
        var onTime = new RouteCandidate(
            "VOY-OK", List.of(), 10, BigDecimal.TEN, deadline, Set.of(CargoType.GENERAL)
        );
        var late = new RouteCandidate(
            "VOY-LATE", List.of(), 35, BigDecimal.TEN,
            deadline.plusDays(5), Set.of(CargoType.GENERAL)
        );
        when(routeProviderPort.findRoutes(query)).thenReturn(List.of(onTime, late));

        // Act
        var result = service.searchByCondition(query);

        // Assert
        assertThat(result).containsExactly(onTime);
        assertThat(result).doesNotContain(late);
    }

    @Test
    @DisplayName("searchByCondition はすべてのルートが期限超過の場合は空リストを返す")
    void searchByConditionはすべてのルートが期限超過の場合は空リストを返す() {
        // Arrange
        var deadline = LocalDate.of(2025, 12, 31);
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", deadline, CargoType.GENERAL, BigDecimal.TEN
        );
        var late = new RouteCandidate(
            "VOY-LATE", List.of(), 35, BigDecimal.TEN,
            deadline.plusDays(1), Set.of(CargoType.GENERAL)
        );
        when(routeProviderPort.findRoutes(query)).thenReturn(List.of(late));

        // Act & Assert
        assertThat(service.searchByCondition(query)).isEmpty();
    }

    @Test
    @DisplayName("searchByCondition は危険物貨物に対応しないルートを除外する")
    void searchByConditionは危険物貨物に対応しないルートを除外する() {
        // Arrange
        var deadline = LocalDate.of(2025, 12, 31);
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", deadline, CargoType.HAZARDOUS, BigDecimal.TEN
        );
        var hazOk = new RouteCandidate(
            "VOY-HAZ", List.of(), 10, BigDecimal.TEN, deadline,
            Set.of(CargoType.GENERAL, CargoType.HAZARDOUS)
        );
        var generalOnly = new RouteCandidate(
            "VOY-GEN", List.of(), 10, BigDecimal.TEN, deadline,
            Set.of(CargoType.GENERAL)
        );
        when(routeProviderPort.findRoutes(query)).thenReturn(List.of(hazOk, generalOnly));

        // Act
        var result = service.searchByCondition(query);

        // Assert
        assertThat(result).containsExactly(hazOk);
        assertThat(result).doesNotContain(generalOnly);
    }

    @Test
    @DisplayName("searchByCondition は冷凍貨物に対応しないルートを除外する")
    void searchByConditionは冷凍貨物に対応しないルートを除外する() {
        // Arrange
        var deadline = LocalDate.of(2025, 12, 31);
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", deadline, CargoType.REFRIGERATED, BigDecimal.TEN
        );
        var refOk = new RouteCandidate(
            "VOY-REF", List.of(), 10, BigDecimal.TEN, deadline,
            Set.of(CargoType.GENERAL, CargoType.REFRIGERATED)
        );
        var hazOnly = new RouteCandidate(
            "VOY-HAZ", List.of(), 10, BigDecimal.TEN, deadline,
            Set.of(CargoType.GENERAL, CargoType.HAZARDOUS)
        );
        when(routeProviderPort.findRoutes(query)).thenReturn(List.of(refOk, hazOnly));

        // Act
        var result = service.searchByCondition(query);

        // Assert
        assertThat(result).containsExactly(refOk);
        assertThat(result).doesNotContain(hazOnly);
    }

    @Test
    @DisplayName("searchByCondition は期限内かつ貨物種別対応のルートのみ返す（複合フィルタ）")
    void searchByConditionは期限内かつ貨物種別対応のルートのみ返す() {
        // Arrange
        var deadline = LocalDate.of(2025, 12, 31);
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", deadline, CargoType.HAZARDOUS, BigDecimal.TEN
        );
        var pass = new RouteCandidate(
            "VOY-PASS", List.of(), 10, BigDecimal.TEN, deadline,
            Set.of(CargoType.GENERAL, CargoType.HAZARDOUS)
        );
        var lateButOk = new RouteCandidate(
            "VOY-LATE", List.of(), 35, BigDecimal.TEN,
            deadline.plusDays(3), Set.of(CargoType.HAZARDOUS)
        );
        var onTimeWrongCargo = new RouteCandidate(
            "VOY-WRONG", List.of(), 5, BigDecimal.TEN, deadline.minusDays(2),
            Set.of(CargoType.GENERAL)
        );
        when(routeProviderPort.findRoutes(query)).thenReturn(List.of(pass, lateButOk, onTimeWrongCargo));

        // Act
        var result = service.searchByCondition(query);

        // Assert
        assertThat(result).containsExactly(pass);
    }

    // Mockito の argThat をスタティックインポートなしで使うためのヘルパー
    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) {
        return org.mockito.ArgumentMatchers.argThat(matcher);
    }
}
