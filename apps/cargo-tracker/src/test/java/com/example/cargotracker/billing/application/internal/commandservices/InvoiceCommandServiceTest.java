package com.example.cargotracker.billing.application.internal.commandservices;

import com.example.cargotracker.billing.domain.model.aggregates.FreightCharge;
import com.example.cargotracker.billing.domain.model.aggregates.FreightId;
import com.example.cargotracker.billing.domain.model.aggregates.Invoice;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;
import com.example.cargotracker.billing.domain.model.commands.ConfirmPaymentCommand;
import com.example.cargotracker.billing.domain.model.commands.GenerateInvoiceCommand;
import com.example.cargotracker.billing.domain.model.repository.FreightChargeRepository;
import com.example.cargotracker.billing.domain.model.repository.InvoiceRepository;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.booking.domain.repository.BookingRepository;
import com.example.cargotracker.shared.domain.model.ShipperId;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceCommandService")
class InvoiceCommandServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private FreightChargeRepository freightChargeRepository;

    @Mock
    private BookingRepository bookingRepository;

    private InvoiceCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new InvoiceCommandService(invoiceRepository, freightChargeRepository, bookingRepository);
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
        String bookingId = BookingId.generate().toString();
        Invoice invoice = Invoice.generate(invoiceId, bookingId, "freight-001",
                new BigDecimal("10000"), LocalDate.now().plusDays(30));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        ConfirmPaymentCommand command = new ConfirmPaymentCommand(invoiceId.value().toString());
        Booking booking = confirmedBooking(bookingId);
        when(bookingRepository.findById(BookingId.of(bookingId))).thenReturn(Optional.of(booking));

        // When
        commandService.confirmPayment(command);

        // Then
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentStatus().name()).isEqualTo("CONFIRMED");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SETTLED);
        verify(bookingRepository).save(booking);
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

    @Test
    @DisplayName("支払い確認時に予約が見つからない場合は IllegalArgumentException をスローする")
    void confirmPayment_予約が見つからない場合はIllegalArgumentExceptionをスローする() {
        InvoiceId invoiceId = InvoiceId.generate();
        String bookingId = BookingId.generate().toString();
        Invoice invoice = Invoice.generate(invoiceId, bookingId, "freight-001",
                new BigDecimal("10000"), LocalDate.now().plusDays(30));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(bookingRepository.findById(BookingId.of(bookingId))).thenReturn(Optional.empty());

        ConfirmPaymentCommand command = new ConfirmPaymentCommand(invoiceId.value().toString());

        assertThatThrownBy(() -> commandService.confirmPayment(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(bookingId);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("同一の輸送料金 ID で重複して精算書を生成しようとすると IllegalStateException をスローする")
    void generateInvoice_重複した輸送料金IDではIllegalStateExceptionをスローする() {
        // Given
        FreightId freightId = FreightId.generate();
        Invoice existingInvoice = Invoice.generate(
                InvoiceId.generate(), "booking-001",
                freightId.value().toString(), new BigDecimal("10000"),
                LocalDate.now().plusDays(30));
        when(invoiceRepository.findByFreightChargeId(freightId.value().toString()))
                .thenReturn(Optional.of(existingInvoice));

        GenerateInvoiceCommand command = new GenerateInvoiceCommand("booking-001", freightId.value().toString());

        // When / Then
        assertThatThrownBy(() -> commandService.generateInvoice(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(freightId.value().toString());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("コマンドの bookingId が輸送料金の bookingId と一致しない場合 IllegalArgumentException をスローする")
    void generateInvoice_bookingIdが一致しない場合はIllegalArgumentExceptionをスローする() {
        // Given
        FreightId freightId = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(freightId, "booking-001", new BigDecimal("10000"));
        charge.applyAdjustment(BigDecimal.ZERO);
        charge.confirm();
        when(freightChargeRepository.findById(freightId)).thenReturn(Optional.of(charge));

        GenerateInvoiceCommand command = new GenerateInvoiceCommand("booking-WRONG", freightId.value().toString());

        // When / Then
        assertThatThrownBy(() -> commandService.generateInvoice(command))
                .isInstanceOf(IllegalArgumentException.class);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("支払期限を指定して精算書を生成できる")
    void generateInvoice_支払期限を指定して精算書を生成できる() {
        FreightId freightId = FreightId.generate();
        FreightCharge charge = FreightCharge.calculate(freightId, "booking-001", new BigDecimal("10000"));
        charge.applyAdjustment(BigDecimal.ZERO);
        charge.confirm();
        when(freightChargeRepository.findById(freightId)).thenReturn(Optional.of(charge));
        LocalDate dueDate = LocalDate.now().plusDays(10);

        commandService.generateInvoice(new GenerateInvoiceCommand("booking-001", freightId.value().toString(), dueDate));

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertThat(captor.getValue().getDueDate()).isEqualTo(dueDate);
    }

    private Booking confirmedBooking(String bookingId) {
        return Booking.reconstitute(
                BookingId.of(bookingId),
                ShipperId.generate(),
                new CargoSpecification(
                        CargoType.GENERAL_CARGO,
                        new BigDecimal("100.0"),
                        null, null, null,
                        1, "テスト品"
                ),
                new TransportCondition(
                        "JPTYO",
                        "SGSIN",
                        LocalDate.now(),
                        LocalDate.now().plusDays(10)
                ),
                BookingStatus.CONFIRMED,
                new AssignedRoute("V001", "JPTYO -> SGSIN", LocalDate.now().plusDays(10))
        );
    }
}
