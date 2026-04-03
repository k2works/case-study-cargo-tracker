package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.commands.ConfirmPaymentCommand;
import com.example.cargotracker.billing.domain.model.commands.GenerateInvoiceCommand;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import com.example.cargotracker.billing.domain.model.repository.InvoiceRepository;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceCommandService")
class InvoiceCommandServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private FreightChargeRepository freightChargeRepository;

    private InvoiceCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new InvoiceCommandService(invoiceRepository, freightChargeRepository);
    }

    @Test
    @DisplayName("確定済み輸送料金から精算書を生成できる")
    void generateInvoice_確定済み輸送料金から精算書を生成できる() {
        // Given
        FreightId freightId = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(freightId, "booking-001", new BigDecimal("10000"));
        charge.applyAdjustment(BigDecimal.ZERO);
        charge.confirm();
        when(freightChargeRepository.findById(freightId)).thenReturn(Optional.of(charge));

        GenerateInvoiceCommand command = new GenerateInvoiceCommand("booking-001", freightId.value().toString());

        // When
        InvoiceId invoiceId = commandService.generateInvoice(command);

        // Then
        assertThat(invoiceId).isNotNull();
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        Invoice saved = captor.getValue();
        assertThat(saved.getBookingId()).isEqualTo("booking-001");
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("10000"));
    }

    @Test
    @DisplayName("DRAFT状態の輸送料金では精算書を生成できない")
    void generateInvoice_DRAFT状態の輸送料金では精算書を生成できない() {
        // Given
        FreightId freightId = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(freightId, "booking-002", new BigDecimal("5000"));
        when(freightChargeRepository.findById(freightId)).thenReturn(Optional.of(charge));

        GenerateInvoiceCommand command = new GenerateInvoiceCommand("booking-002", freightId.value().toString());

        // When / Then
        assertThatThrownBy(() -> commandService.generateInvoice(command))
                .isInstanceOf(IllegalStateException.class);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("輸送料金が見つからない場合はIllegalArgumentExceptionをスローする")
    void generateInvoice_輸送料金が見つからない場合はIllegalArgumentExceptionをスローする() {
        // Given
        FreightId freightId = FreightId.generate();
        when(freightChargeRepository.findById(freightId)).thenReturn(Optional.empty());

        GenerateInvoiceCommand command = new GenerateInvoiceCommand("booking-003", freightId.value().toString());

        // When / Then
        assertThatThrownBy(() -> commandService.generateInvoice(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(freightId.value().toString());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("精算書の支払いを確認できる")
    void confirmPayment_精算書の支払いを確認できる() {
        // Given
        InvoiceId invoiceId = InvoiceId.generate();
        Invoice invoice = Invoice.generate(invoiceId, "booking-001", "freight-001",
                new BigDecimal("10000"), LocalDate.now().plusDays(30));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        ConfirmPaymentCommand command = new ConfirmPaymentCommand(invoiceId.value().toString());

        // When
        commandService.confirmPayment(command);

        // Then
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentStatus().name()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("精算書が見つからない場合はIllegalArgumentExceptionをスローする")
    void confirmPayment_精算書が見つからない場合はIllegalArgumentExceptionをスローする() {
        // Given
        InvoiceId invoiceId = InvoiceId.generate();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        ConfirmPaymentCommand command = new ConfirmPaymentCommand(invoiceId.value().toString());

        // When / Then
        assertThatThrownBy(() -> commandService.confirmPayment(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(invoiceId.value().toString());
        verify(invoiceRepository, never()).save(any());
    }
}
