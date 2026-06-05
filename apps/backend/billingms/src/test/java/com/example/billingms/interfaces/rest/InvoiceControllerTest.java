package com.example.billingms.interfaces.rest;

import com.example.billingms.application.InvoiceQueryService;
import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.commands.IssueInvoiceCommand;
import com.example.billingms.domain.commands.RecordPaymentCommand;
import com.example.billingms.domain.projections.InvoiceLine;
import com.example.billingms.domain.projections.InvoiceSummary;
import com.example.billingms.interfaces.rest.dto.CalculateInvoiceRequest;
import com.example.billingms.interfaces.rest.dto.RecordPaymentRequest;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 9, 20, 10, 0);

    @Mock
    private CommandGateway commandGateway;

    @Mock
    private InvoiceQueryService queryService;

    private InvoiceController controller;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        controller = new InvoiceController(commandGateway, queryService, fixedClock);
    }

    private CalculateInvoiceRequest validRequest() {
        return new CalculateInvoiceRequest(
                "B-001", "S-001",
                new BigDecimal("5300"), new BigDecimal("1200"),
                "GENERAL", 8, "JPY"
        );
    }

    @Test
    @DisplayName("US21: POST /invoices で 202 Accepted + invoiceId を返し CalculateInvoiceCommand を送信")
    void POST_calculate_success() {
        ResponseEntity<InvoiceController.InvoiceCreationResponse> response =
                controller.calculate(validRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().invoiceId()).isNotBlank();

        ArgumentCaptor<CalculateInvoiceCommand> captor = ArgumentCaptor.forClass(CalculateInvoiceCommand.class);
        org.mockito.Mockito.verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo("B-001");
        assertThat(captor.getValue().shipperId()).isEqualTo("S-001");
        assertThat(captor.getValue().transport().distanceKm()).isEqualByComparingTo("5300");
    }

    @Test
    @DisplayName("US21: bookingId が空ならば 400")
    void POST_calculate_400_bookingId_blank() {
        CalculateInvoiceRequest invalid = new CalculateInvoiceRequest(
                "", "S-001", new BigDecimal("5300"), new BigDecimal("1200"),
                "GENERAL", 8, "JPY");

        ResponseEntity<InvoiceController.InvoiceCreationResponse> response = controller.calculate(invalid);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US21: 距離が null ならば 400")
    void POST_calculate_400_distance_null() {
        CalculateInvoiceRequest invalid = new CalculateInvoiceRequest(
                "B-001", "S-001", null, new BigDecimal("1200"),
                "GENERAL", 8, "JPY");

        ResponseEntity<InvoiceController.InvoiceCreationResponse> response = controller.calculate(invalid);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US21: TransportRecord 不変条件違反は 400")
    void POST_calculate_400_weight_zero() {
        CalculateInvoiceRequest invalid = new CalculateInvoiceRequest(
                "B-001", "S-001", new BigDecimal("5300"), BigDecimal.ZERO,
                "GENERAL", 8, "JPY");

        ResponseEntity<InvoiceController.InvoiceCreationResponse> response = controller.calculate(invalid);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US21: GET /invoices/{id} で投影を返却（lines 含む）")
    void GET_findByInvoiceId_success() {
        InvoiceSummary summary = new InvoiceSummary();
        summary.setInvoiceId("INV-001");
        summary.setBookingId("B-001");
        summary.setShipperId("S-001");
        summary.setBasicAmount(new BigDecimal("330000"));
        summary.setDiscountAmount(BigDecimal.ZERO);
        summary.setAdjustmentAmount(BigDecimal.ZERO);
        summary.setTotalAmount(new BigDecimal("330000"));
        summary.setCurrency("JPY");
        summary.setBillingStatus("CALCULATED");

        InvoiceLine basicLine = new InvoiceLine();
        basicLine.setInvoiceId("INV-001");
        basicLine.setLineSeq(1);
        basicLine.setLineType("BASIC");
        basicLine.setDescription("基本料金");
        basicLine.setAmount(new BigDecimal("330000"));

        when(queryService.findByInvoiceId("INV-001")).thenReturn(summary);
        when(queryService.findLinesByInvoiceId("INV-001")).thenReturn(List.of(basicLine));

        var response = controller.findByInvoiceId("INV-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().invoiceId()).isEqualTo("INV-001");
        assertThat(response.getBody().billingStatus()).isEqualTo("CALCULATED");
        assertThat(response.getBody().lines()).hasSize(1);
        assertThat(response.getBody().lines().get(0).lineType()).isEqualTo("BASIC");
    }

    @Test
    @DisplayName("US21: 存在しない invoiceId は 404")
    void GET_findByInvoiceId_404() {
        when(queryService.findByInvoiceId("INV-NOTEXIST")).thenReturn(null);

        var response = controller.findByInvoiceId("INV-NOTEXIST");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US23: GET /invoices でページネーション付き一覧を返却")
    void GET_findAll_pagination() {
        InvoiceSummary summary = new InvoiceSummary();
        summary.setInvoiceId("INV-001");
        summary.setBasicAmount(BigDecimal.ZERO);
        summary.setDiscountAmount(BigDecimal.ZERO);
        summary.setAdjustmentAmount(BigDecimal.ZERO);
        summary.setTotalAmount(BigDecimal.ZERO);
        summary.setCurrency("JPY");
        summary.setBillingStatus("CALCULATED");
        when(queryService.findAll(0, 20)).thenReturn(List.of(summary));
        when(queryService.findLinesByInvoiceId(any())).thenReturn(List.of());
        when(queryService.count()).thenReturn(5L);

        var response = controller.findAll(0, 20, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().totalCount()).isEqualTo(5L);
        assertThat(response.getBody().page()).isZero();
        assertThat(response.getBody().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("US23: 不正な size は 20 に補正")
    void GET_findAll_size_invalid_falls_back_to_20() {
        when(queryService.findAll(0, 20)).thenReturn(List.of());
        when(queryService.count()).thenReturn(0L);

        var response = controller.findAll(0, 0, null);
        assertThat(response.getBody().size()).isEqualTo(20);

        response = controller.findAll(0, 500, null);
        assertThat(response.getBody().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("US23 T4.3: GET /invoices?status=INVOICED でステータスフィルタ")
    void GET_findAll_status_filter() {
        InvoiceSummary summary = new InvoiceSummary();
        summary.setInvoiceId("INV-001");
        summary.setBasicAmount(BigDecimal.ZERO);
        summary.setDiscountAmount(BigDecimal.ZERO);
        summary.setAdjustmentAmount(BigDecimal.ZERO);
        summary.setTotalAmount(BigDecimal.ZERO);
        summary.setCurrency("JPY");
        summary.setBillingStatus("INVOICED");
        when(queryService.findByStatus("INVOICED", 0, 20)).thenReturn(List.of(summary));
        when(queryService.countByStatus("INVOICED")).thenReturn(3L);
        when(queryService.findLinesByInvoiceId(any())).thenReturn(List.of());

        var response = controller.findAll(0, 20, "INVOICED");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalCount()).isEqualTo(3L);
        assertThat(response.getBody().items()).hasSize(1);
    }

    @Test
    @DisplayName("US23 T4.3: POST /invoices/{id}/issue で IssueInvoiceCommand 送信")
    void POST_issue_success() {
        ResponseEntity<Void> response = controller.issue("INV-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        ArgumentCaptor<IssueInvoiceCommand> captor = ArgumentCaptor.forClass(IssueInvoiceCommand.class);
        org.mockito.Mockito.verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue().invoiceId()).isEqualTo("INV-001");
    }

    @Test
    @DisplayName("US23 T4.3: POST /invoices/{id}/issue で invoiceId が空ならば 400")
    void POST_issue_400_blank_id() {
        ResponseEntity<Void> response = controller.issue("");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US23 T4.3: POST /invoices/{id}/payments で RecordPaymentCommand 送信 + paymentId 返却")
    void POST_payments_success() {
        RecordPaymentRequest request = new RecordPaymentRequest(
                new BigDecimal("330000"), "JPY",
                LocalDateTime.of(2026, 9, 22, 15, 0),
                "BANK_TRANSFER", "TXN-001");

        ResponseEntity<InvoiceController.PaymentCreationResponse> response =
                controller.recordPayment("INV-001", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().paymentId()).isNotBlank();

        ArgumentCaptor<RecordPaymentCommand> captor = ArgumentCaptor.forClass(RecordPaymentCommand.class);
        org.mockito.Mockito.verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue().invoiceId()).isEqualTo("INV-001");
        assertThat(captor.getValue().paidAmount()).isEqualByComparingTo("330000");
        assertThat(captor.getValue().currency()).isEqualTo("JPY");
        assertThat(captor.getValue().paymentMethod()).isEqualTo("BANK_TRANSFER");
    }

    @Test
    @DisplayName("US23 T4.3: paidAt 未指定なら Clock 由来の現在時刻が使われる")
    void POST_payments_default_paidAt() {
        RecordPaymentRequest request = new RecordPaymentRequest(
                new BigDecimal("330000"), "JPY", null, "MANUAL", null);

        ResponseEntity<InvoiceController.PaymentCreationResponse> response =
                controller.recordPayment("INV-001", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        ArgumentCaptor<RecordPaymentCommand> captor = ArgumentCaptor.forClass(RecordPaymentCommand.class);
        org.mockito.Mockito.verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue().paidAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("US23 T4.3: paidAmount が null ならば 400")
    void POST_payments_400_paidAmount_null() {
        RecordPaymentRequest request = new RecordPaymentRequest(
                null, "JPY", null, "BANK_TRANSFER", null);

        ResponseEntity<InvoiceController.PaymentCreationResponse> response =
                controller.recordPayment("INV-001", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US23 T4.3: GET /invoices/overdue で督促対象一覧を返却")
    void GET_overdue_success() {
        InvoiceSummary overdue = new InvoiceSummary();
        overdue.setInvoiceId("INV-OVR-001");
        overdue.setBasicAmount(BigDecimal.ZERO);
        overdue.setDiscountAmount(BigDecimal.ZERO);
        overdue.setAdjustmentAmount(BigDecimal.ZERO);
        overdue.setTotalAmount(BigDecimal.ZERO);
        overdue.setCurrency("JPY");
        overdue.setBillingStatus("INVOICED");
        when(queryService.findOverdueCandidates()).thenReturn(List.of(overdue));
        when(queryService.findLinesByInvoiceId(any())).thenReturn(List.of());

        var response = controller.findOverdue();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().totalCount()).isEqualTo(1L);
    }
}
