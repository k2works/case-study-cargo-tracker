package com.example.cargotracker.billing.application;

import com.example.cargotracker.billing.application.internal.commandservices.BookingNotFoundException;
import com.example.cargotracker.billing.application.internal.commandservices.CalculateFreightCommandService;
import com.example.cargotracker.billing.application.internal.outboundservices.FreightBookingQueryPort;
import com.example.cargotracker.billing.application.internal.outboundservices.FreightBookingQueryPort.FreightBookingSummary;
import com.example.cargotracker.billing.application.internal.outboundservices.ShipperDiscountQueryPort;
import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.commands.CalculateFreightCommand;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import com.example.cargotracker.billing.domain.model.services.DiscountPolicy;
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
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CalculateFreightCommandService")
class CalculateFreightCommandServiceTest {

    @Mock
    private FreightChargeRepository freightChargeRepository;

    @Mock
    private FreightBookingQueryPort freightBookingQueryPort;

    @Mock
    private ShipperDiscountQueryPort shipperDiscountQueryPort;

    @Mock
    private FreightCalculationService freightCalculationService;

    private CalculateFreightCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new CalculateFreightCommandService(
                freightChargeRepository,
                freightBookingQueryPort,
                shipperDiscountQueryPort,
                new DiscountPolicy(),
                freightCalculationService
        );
    }

    @Test
    @DisplayName("確定済み予約から料金を算出して FreightId を返す")
    void calculate_確定済み予約から料金を算出してFreightIdを返す() {
        // Given
        String bookingId = "booking-001";
        BigDecimal weightKg = new BigDecimal("100.0");
        BigDecimal baseAmount = new BigDecimal("100");
        FreightBookingSummary summary = new FreightBookingSummary(
                bookingId, CargoType.GENERAL_CARGO, weightKg, "JPTYO", "SGSIN",
                "JPTYO→SGSIN", LocalDate.of(2026, 6, 1), 1, new BigDecimal("5300"));

        when(freightBookingQueryPort.findCalculableBookingById(bookingId))
                .thenReturn(Optional.of(summary));
        when(shipperDiscountQueryPort.findDiscountRateByBookingId(bookingId))
                .thenReturn(BigDecimal.ZERO);
        when(freightCalculationService.calculateBaseAmount(weightKg, CargoType.GENERAL_CARGO))
                .thenReturn(baseAmount);
        doNothing().when(freightChargeRepository).save(any(FreightCharge.class));

        // When
        FreightId result = commandService.calculate(new CalculateFreightCommand(bookingId, null));

        // Then
        assertThat(result).isNotNull();
        verify(freightChargeRepository).save(any(FreightCharge.class));
    }

    @Test
    @DisplayName("予約が見つからない場合は BookingNotFoundException をスロー")
    void calculate_予約が見つからない場合はBookingNotFoundExceptionをスロー() {
        // Given
        String bookingId = "booking-not-found";
        when(freightBookingQueryPort.findCalculableBookingById(bookingId))
                .thenReturn(Optional.empty());

        // When / Then
        CalculateFreightCommand cmd82 = new CalculateFreightCommand(bookingId, null);
        assertThatThrownBy(() -> commandService.calculate(cmd82))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(bookingId);
        verify(freightChargeRepository, never()).save(any());
    }

    @Test
    @DisplayName("予約が未確定の場合（Port が empty を返す）は BookingNotFoundException をスロー")
    void calculate_予約が未確定の場合はBookingNotFoundExceptionをスロー() {
        // Given
        String bookingId = "booking-provisional";
        when(freightBookingQueryPort.findCalculableBookingById(bookingId))
                .thenReturn(Optional.empty());

        // When / Then
        CalculateFreightCommand cmd97 = new CalculateFreightCommand(bookingId, null);
        assertThatThrownBy(() -> commandService.calculate(cmd97))
                .isInstanceOf(BookingNotFoundException.class);
        verify(freightChargeRepository, never()).save(any());
    }

    @Test
    @DisplayName("調整額付きで料金を算出すると totalAmount に反映される")
    void calculate_調整額付きで料金を算出するとTotalAmountに反映される() {
        String bookingId = "booking-001";
        BigDecimal weightKg = new BigDecimal("100.0");
        BigDecimal baseAmount = new BigDecimal("100");
        FreightBookingSummary summary = new FreightBookingSummary(
                bookingId, CargoType.GENERAL_CARGO, weightKg, "JPTYO", "SGSIN",
                "JPTYO→SGSIN", LocalDate.of(2026, 6, 1), 2, new BigDecimal("5300"));

        when(freightBookingQueryPort.findCalculableBookingById(bookingId))
                .thenReturn(Optional.of(summary));
        when(shipperDiscountQueryPort.findDiscountRateByBookingId(bookingId))
                .thenReturn(BigDecimal.ZERO);
        when(freightCalculationService.calculateBaseAmount(weightKg, CargoType.GENERAL_CARGO))
                .thenReturn(baseAmount);

        commandService.calculate(new CalculateFreightCommand(bookingId, new BigDecimal("-20")));

        ArgumentCaptor<FreightCharge> captor = ArgumentCaptor.forClass(FreightCharge.class);
        verify(freightChargeRepository).save(captor.capture());
        assertThat(captor.getValue().getAdjustmentAmount()).isEqualByComparingTo(new BigDecimal("-20"));
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo(new BigDecimal("80"));
    }

    @Test
    @DisplayName("法人荷主の割引率は料金算出時に自動適用される")
    void calculate_法人荷主の割引率は料金算出時に自動適用される() {
        String bookingId = "booking-corporate";
        BigDecimal weightKg = new BigDecimal("180.0");
        BigDecimal baseAmount = new BigDecimal("180");
        FreightBookingSummary summary = new FreightBookingSummary(
                bookingId, CargoType.GENERAL_CARGO, weightKg, "JPTYO", "SGSIN",
                "JPTYO→SGSIN", LocalDate.of(2026, 6, 1), 1, new BigDecimal("5300"));

        when(freightBookingQueryPort.findCalculableBookingById(bookingId))
                .thenReturn(Optional.of(summary));
        when(shipperDiscountQueryPort.findDiscountRateByBookingId(bookingId))
                .thenReturn(new BigDecimal("10"));
        when(freightCalculationService.calculateBaseAmount(weightKg, CargoType.GENERAL_CARGO))
                .thenReturn(baseAmount);

        commandService.calculate(new CalculateFreightCommand(bookingId, null));

        ArgumentCaptor<FreightCharge> captor = ArgumentCaptor.forClass(FreightCharge.class);
        verify(freightChargeRepository).save(captor.capture());
        assertThat(captor.getValue().getAdjustmentAmount()).isEqualByComparingTo(new BigDecimal("-18.00"));
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo(new BigDecimal("162.00"));
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
