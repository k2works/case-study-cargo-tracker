package com.example.cargotracker.routing;

import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.application.internal.outboundservices.BookingSnapshot;
import com.example.cargotracker.routing.application.internal.queryservices.BookingDataNotFoundException;
import com.example.cargotracker.routing.application.internal.queryservices.RouteDesignConditionQueryService;
import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteDesignCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RouteDesignConditionQueryService")
class RouteDesignConditionQueryServiceTest {

    @Mock
    private BookingQueryPort bookingQueryPort;

    private RouteDesignConditionQueryService service;

    @BeforeEach
    void setUp() {
        service = new RouteDesignConditionQueryService(bookingQueryPort);
    }

    @Test
    @DisplayName("予約が存在する場合、BookingSnapshot から RouteDesignCondition を生成して返す")
    void findByBookingId_予約あり() {
        // Arrange
        var bookingId = UUID.randomUUID();
        var snapshot = new BookingSnapshot(
            "JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 30),
            CargoType.GENERAL,
            new BigDecimal("500.0")
        );
        when(bookingQueryPort.findById(bookingId)).thenReturn(Optional.of(snapshot));

        // Act
        RouteDesignCondition result = service.findByBookingId(bookingId);

        // Assert
        assertThat(result.bookingId()).isEqualTo(bookingId);
        assertThat(result.originLocode()).isEqualTo("JPTYO");
        assertThat(result.destinationLocode()).isEqualTo("SGSIN");
        assertThat(result.requestedArrivalDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(result.cargoType()).isEqualTo(CargoType.GENERAL);
        assertThat(result.weightKg()).isEqualByComparingTo(new BigDecimal("500.0"));
        assertThat(result.isComplete()).isTrue();
    }

    @Test
    @DisplayName("originLocode が null の予約の場合、isComplete() は false を返す")
    void findByBookingId_originLocode_null_isComplete_false() {
        // Arrange
        var bookingId = UUID.randomUUID();
        var snapshot = new BookingSnapshot(
            null, "SGSIN",
            LocalDate.of(2026, 6, 30),
            CargoType.GENERAL,
            new BigDecimal("500.0")
        );
        when(bookingQueryPort.findById(bookingId)).thenReturn(Optional.of(snapshot));

        // Act
        RouteDesignCondition result = service.findByBookingId(bookingId);

        // Assert
        assertThat(result.originLocode()).isNull();
        assertThat(result.isComplete()).isFalse();
    }

    @Test
    @DisplayName("予約が存在しない場合は BookingDataNotFoundException をスローする")
    void findByBookingId_予約なし() {
        // Arrange
        var bookingId = UUID.randomUUID();
        when(bookingQueryPort.findById(bookingId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.findByBookingId(bookingId))
            .isInstanceOf(BookingDataNotFoundException.class)
            .hasMessageContaining(bookingId.toString());
    }
}
