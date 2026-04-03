package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.application.internal.outboundservices.ShipperDiscountQueryPort;
import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.commands.ApplyDiscountCommand;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import com.example.cargotracker.billing.domain.model.services.DiscountPolicy;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplyDiscountCommandService")
class ApplyDiscountCommandServiceTest {

    @Mock
    private FreightChargeRepository freightChargeRepository;

    @Mock
    private ShipperDiscountQueryPort shipperDiscountQueryPort;

    private ApplyDiscountCommandService commandService;

    @BeforeEach
    void setUp() {
        // DiscountPolicy は実インスタンスを使用する
        commandService = new ApplyDiscountCommandService(
                freightChargeRepository,
                shipperDiscountQueryPort,
                new DiscountPolicy());
    }

    @Test
    @DisplayName("法人割引を適用すると調整額がマイナス値で設定される")
    void applyDiscount_法人割引を適用すると調整額がマイナス値で設定される() {
        // Given
        FreightId freightId = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(freightId, "booking-001", new BigDecimal("10000"));
        when(freightChargeRepository.findById(freightId)).thenReturn(Optional.of(charge));
        when(shipperDiscountQueryPort.findDiscountRateByBookingId("booking-001"))
                .thenReturn(new BigDecimal("10"));

        ApplyDiscountCommand command = new ApplyDiscountCommand(
                freightId.value().toString(), "booking-001");

        // When
        commandService.applyDiscount(command);

        // Then
        ArgumentCaptor<FreightCharge> captor = ArgumentCaptor.forClass(FreightCharge.class);
        verify(freightChargeRepository).save(captor.capture());
        FreightCharge saved = captor.getValue();
        assertThat(saved.getAdjustmentAmount()).isEqualByComparingTo(new BigDecimal("-1000.00"));
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(new BigDecimal("9000.00"));
    }

    @Test
    @DisplayName("割引率0の場合は調整額がゼロになる")
    void applyDiscount_割引率0の場合は調整額がゼロになる() {
        // Given
        FreightId freightId = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(freightId, "booking-002", new BigDecimal("5000"));
        when(freightChargeRepository.findById(freightId)).thenReturn(Optional.of(charge));
        when(shipperDiscountQueryPort.findDiscountRateByBookingId("booking-002"))
                .thenReturn(BigDecimal.ZERO);

        ApplyDiscountCommand command = new ApplyDiscountCommand(
                freightId.value().toString(), "booking-002");

        // When
        commandService.applyDiscount(command);

        // Then
        ArgumentCaptor<FreightCharge> captor = ArgumentCaptor.forClass(FreightCharge.class);
        verify(freightChargeRepository).save(captor.capture());
        assertThat(captor.getValue().getAdjustmentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    @DisplayName("CONFIRMED状態の輸送料金には割引を適用できない")
    void applyDiscount_CONFIRMED状態の輸送料金には割引を適用できない() {
        // Given
        FreightId freightId = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(freightId, "booking-003", new BigDecimal("8000"));
        charge.applyAdjustment(BigDecimal.ZERO);
        charge.confirm();
        when(freightChargeRepository.findById(freightId)).thenReturn(Optional.of(charge));
        when(shipperDiscountQueryPort.findDiscountRateByBookingId("booking-003"))
                .thenReturn(new BigDecimal("15"));

        ApplyDiscountCommand command = new ApplyDiscountCommand(
                freightId.value().toString(), "booking-003");

        // When / Then
        assertThatThrownBy(() -> commandService.applyDiscount(command))
                .isInstanceOf(IllegalStateException.class);
        verify(freightChargeRepository, never()).save(any());
    }

    @Test
    @DisplayName("輸送料金が見つからない場合はIllegalArgumentExceptionをスローする")
    void applyDiscount_輸送料金が見つからない場合はIllegalArgumentExceptionをスローする() {
        // Given
        FreightId freightId = FreightId.generate();
        when(freightChargeRepository.findById(freightId)).thenReturn(Optional.empty());

        ApplyDiscountCommand command = new ApplyDiscountCommand(
                freightId.value().toString(), "booking-004");

        // When / Then
        assertThatThrownBy(() -> commandService.applyDiscount(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(freightId.value().toString());
        verify(freightChargeRepository, never()).save(any());
    }
}
