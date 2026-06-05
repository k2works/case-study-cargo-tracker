package com.example.billingms.infrastructure.outboundservices.notification;

import com.example.billingms.config.NotificationProperties;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link SendGridNotificationAcl}（billingms）の単体テスト（IT8 T3.2 / ADR-0018）。
 */
class SendGridNotificationAclTest {

    private static final NotificationProperties.Templates TEMPLATES =
            new NotificationProperties.Templates(
                    "d-invoice-issued-001",
                    "d-payment-received-002",
                    "d-overdue-003"
            );

    private static final NotificationProperties.SendGrid SENDGRID_CONFIG =
            new NotificationProperties.SendGrid(
                    "SG.test", "noreply@example.com", "Cargo Tracker", TEMPLATES);

    private static final NotificationProperties PROPERTIES =
            new NotificationProperties("sendgrid", SENDGRID_CONFIG);

    private SendGrid sendGridClient;
    private MeterRegistry registry;
    private SendGridNotificationAcl acl;

    @BeforeEach
    void setUp() throws IOException {
        sendGridClient = mock(SendGrid.class);
        registry = new SimpleMeterRegistry();
        acl = new SendGridNotificationAcl(sendGridClient, PROPERTIES, registry);

        Response ok = new Response();
        ok.setStatusCode(202);
        ok.setBody("{}");
        when(sendGridClient.api(any(Request.class))).thenReturn(ok);
    }

    private double counter(String status) {
        var c = registry.find("notification.sent")
                .tag("status", status)
                .tag("channel", "sendgrid")
                .counter();
        return c == null ? 0.0 : c.count();
    }

    @Test
    @DisplayName("US23: invoiceIssued は invoiceNumber / paymentDue / totalAmount を dynamic data に乗せる")
    void invoiceIssued() throws IOException {
        acl.notifyInvoiceIssued(
                "INV-001", "S-001", "INV-20260820-0001",
                LocalDate.of(2026, 9, 19), new BigDecimal("330000"));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGridClient).api(captor.capture());
        String body = captor.getValue().getBody();
        assertThat(body).contains("d-invoice-issued-001");
        assertThat(body).contains("\"invoiceNumber\":\"INV-20260820-0001\"");
        assertThat(body).contains("\"paymentDue\":\"2026-09-19\"");
        assertThat(body).contains("\"totalAmount\":\"330000\"");
        assertThat(counter("success")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("US23: paymentReceived は paymentId / paidAmount を含む")
    void paymentReceived() throws IOException {
        acl.notifyPaymentReceived("INV-001", "S-001", "PAY-001", new BigDecimal("330000"));

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGridClient).api(captor.capture());
        String body = captor.getValue().getBody();
        assertThat(body).contains("d-payment-received-002");
        assertThat(body).contains("\"paymentId\":\"PAY-001\"");
        assertThat(body).contains("\"paidAmount\":\"330000\"");
    }

    @Test
    @DisplayName("US23: overdue は invoiceId / shipperId のみ")
    void overdue() throws IOException {
        acl.notifyOverdue("INV-001", "S-001");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGridClient).api(captor.capture());
        assertThat(captor.getValue().getBody()).contains("d-overdue-003");
    }

    @Test
    @DisplayName("ADR-0018: IOException でも業務フローは止まらず failure counter が増える")
    void ioExceptionDoesNotPropagate() throws IOException {
        when(sendGridClient.api(any())).thenThrow(new IOException("network error"));

        acl.notifyInvoiceIssued("INV-001", "S-001", "INV-A", LocalDate.now(), new BigDecimal("1"));

        assertThat(counter("success")).isZero();
        assertThat(counter("failure")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("ADR-0018: 4xx 応答でも業務フローは止まらず failure counter が増える")
    void httpErrorIncrementsFailure() throws IOException {
        Response err = new Response();
        err.setStatusCode(401);
        err.setBody("{\"error\":\"invalid api key\"}");
        when(sendGridClient.api(any())).thenReturn(err);

        acl.notifyOverdue("INV-001", "S-001");

        assertThat(counter("success")).isZero();
        assertThat(counter("failure")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("ADR-0018: テンプレート ID 未設定（空文字）は API 未呼出 + failure counter")
    void emptyTemplateIdSkips() throws IOException {
        NotificationProperties emptyProps = new NotificationProperties(
                "sendgrid",
                new NotificationProperties.SendGrid(
                        "SG.test", "noreply@example.com", "Test",
                        new NotificationProperties.Templates("", "x", "x")
                ));
        SendGridNotificationAcl emptyAcl = new SendGridNotificationAcl(
                sendGridClient, emptyProps, registry);

        emptyAcl.notifyInvoiceIssued("INV-001", "S-001", "INV-X", LocalDate.now(), BigDecimal.ONE);

        verify(sendGridClient, never()).api(any());
        assertThat(counter("failure")).isEqualTo(1.0);
    }
}
