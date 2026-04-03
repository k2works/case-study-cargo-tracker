package com.example.cargotracker.billing.application;

import com.example.cargotracker.billing.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.billing.application.internal.commandservices.CalculateFreightCommandService;
import com.example.cargotracker.billing.application.internal.outboundservices.FreightBookingQueryPort;
import com.example.cargotracker.billing.application.internal.outboundservices.FreightBookingQueryPort.FreightBookingSummary;
import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.commands.CalculateFreightCommand;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import com.example.cargotracker.billing.domain.model.services.FreightCalculationService;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CalculateFreightCommandService")
class CalculateFreightCommandServiceTest {

    @Mock
    private FreightChargeRepository freightChargeRepository;

    @Mock
    private FreightBookingQueryPort freightBookingQueryPort;

    @Mock
    private FreightCalculationService freightCalculationService;

    private CalculateFreightCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new CalculateFreightCommandService(
                freightChargeRepository, freightBookingQueryPort, freightCalculationService);
    }

    @Test
    @DisplayName("確定済み予約から料金を算出して FreightId を返す")
    void calculate_確定済み予約から料金を算出してFreightIdを返す() {
        // Given
        String bookingId = "booking-001";
        BigDecimal weightKg = new BigDecimal("100.0");
        BigDecimal baseAmount = new BigDecimal("100");
        FreightBookingSummary summary = new FreightBookingSummary(
                bookingId, CargoType.GENERAL_CARGO, weightKg, "JPTYO", "SGSIN");

        when(freightBookingQueryPort.findConfirmedBookingById(bookingId))
                .thenReturn(Optional.of(summary));
        when(freightCalculationService.calculateBaseAmount(weightKg, CargoType.GENERAL_CARGO))
                .thenReturn(baseAmount);
        doNothing().when(freightChargeRepository).save(any(FreightCharge.class));

        // When
        FreightId result = commandService.calculate(new CalculateFreightCommand(bookingId));

        // Then
        assertThat(result).isNotNull();
        verify(freightChargeRepository).save(any(FreightCharge.class));
    }

    @Test
    @DisplayName("予約が見つからない場合は BookingNotFoundException をスロー")
    void calculate_予約が見つからない場合はBookingNotFoundExceptionをスロー() {
        // Given
        String bookingId = "booking-not-found";
        when(freightBookingQueryPort.findConfirmedBookingById(bookingId))
                .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> commandService.calculate(new CalculateFreightCommand(bookingId)))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(bookingId);
        verify(freightChargeRepository, never()).save(any());
    }

    @Test
    @DisplayName("予約が未確定の場合（Port が empty を返す）は BookingNotFoundException をスロー")
    void calculate_予約が未確定の場合はBookingNotFoundExceptionをスロー() {
        // Given
        String bookingId = "booking-provisional";
        when(freightBookingQueryPort.findConfirmedBookingById(bookingId))
                .thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> commandService.calculate(new CalculateFreightCommand(bookingId)))
                .isInstanceOf(BookingNotFoundException.class);
        verify(freightChargeRepository, never()).save(any());
    }

    @Test
    @DisplayName("DRAFT 料金チャージを確定する")
    void confirm_DRAFT料金チャージを確定する() {
        // Given
        FreightId id = FreightId.generate();
        FreightCharge draftCharge = FreightCharge.calculate(id, "booking-001", new BigDecimal("100"));
        when(freightChargeRepository.findById(id)).thenReturn(Optional.of(draftCharge));
        doNothing().when(freightChargeRepository).save(draftCharge);

        // When
        commandService.confirm(id);

        // Then
        ArgumentCaptor<FreightCharge> captor = ArgumentCaptor.forClass(FreightCharge.class);
        verify(freightChargeRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus().name()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("存在しない Id は IllegalArgumentException をスロー")
    void confirm_存在しないIdはIllegalArgumentExceptionをスロー() {
        // Given
        FreightId id = FreightId.generate();
        when(freightChargeRepository.findById(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> commandService.confirm(id))
                .isInstanceOf(IllegalArgumentException.class);
        verify(freightChargeRepository, never()).save(any());
    }
}
