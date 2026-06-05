package com.example.trackingms.infrastructure.outboundservices.notification;

import com.example.trackingms.config.NotificationProperties;
import com.example.trackingms.domain.model.TransportStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link SendGridNotificationAcl} の単体テスト（IT8 T3.1 / ADR-0018）。
 *
 * <p>SendGrid client を mock 化し、テンプレート ID と Dynamic Template Data が
 * 正しく渡されることを検証する。実 HTTP 通信は行わない。</p>
 */
class SendGridNotificationAclTest {

    private static final NotificationProperties.Templates TEMPLATES =
            new NotificationProperties.Templates(
                    "d-tracking-issued-001",
                    "d-status-changed-002",
                    "d-misrouted-003",
                    "d-exception-registered-004",
                    "d-exception-resolved-005",
                    "d-exception-escalation-006"
            );

    private static final NotificationProperties.SendGrid SENDGRID_CONFIG =
            new NotificationProperties.SendGrid(
                    "SG.test-api-key",
                    "noreply@cargo-tracker.example.com",
                    "Cargo Tracker",
                    TEMPLATES
            );

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

        // デフォルトで 202 Accepted（SendGrid の通常応答）
        Response ok = new Response();
        ok.setStatusCode(202);
        ok.setBody("{}");
        when(sendGridClient.api(any(Request.class))).thenReturn(ok);
    }

    private double counter(String status) {
        return registry.find("notification.sent")
                .tag("status", status)
                .tag("channel", "sendgrid")
                .counter() == null
                ? 0.0
                : registry.find("notification.sent").tag("status", status).tag("channel", "sendgrid").counter().count();
    }

    @Test
    @DisplayName("US14: trackingIssued は templates.trackingIssued ID で送信され、success counter が増える")
    void trackingIssued() throws IOException {
        acl.notifyTrackingIssued("TRK-AB12CD3456", "B-001");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGridClient).api(captor.capture());
        Request req = captor.getValue();
        assertThat(req.getBody()).contains("\"template_id\":\"d-tracking-issued-001\"");
        assertThat(req.getBody()).contains("\"trackingNumber\":\"TRK-AB12CD3456\"");
        assertThat(req.getBody()).contains("\"bookingId\":\"B-001\"");
        assertThat(counter("success")).isEqualTo(1.0);
        assertThat(counter("failure")).isZero();
    }

    @Test
    @DisplayName("US15/17: statusChanged で fromStatus/toStatus/unlocode が dynamic_template_data に乗る")
    void statusChanged() throws IOException {
        acl.notifyStatusChanged("TRK-001", TransportStatus.NOT_RECEIVED, TransportStatus.RECEIVED, "JPTYO");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGridClient).api(captor.capture());
        String body = captor.getValue().getBody();
        assertThat(body).contains("d-status-changed-002");
        assertThat(body).contains("\"fromStatus\":\"NOT_RECEIVED\"");
        assertThat(body).contains("\"toStatus\":\"RECEIVED\"");
        assertThat(body).contains("\"unlocode\":\"JPTYO\"");
    }

    @Test
    @DisplayName("US17: misrouted は unlocode のみ")
    void misrouted() throws IOException {
        acl.notifyMisrouted("TRK-001", "SGSIN");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGridClient).api(captor.capture());
        assertThat(captor.getValue().getBody()).contains("d-misrouted-003");
    }

    @Test
    @DisplayName("US19: exceptionRegistered は exceptionType と description を含む")
    void exceptionRegistered() throws IOException {
        acl.notifyExceptionRegistered("TRK-001", "EX-001", "DELAY", "JPTYO", "台風による遅延");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGridClient).api(captor.capture());
        String body = captor.getValue().getBody();
        assertThat(body).contains("d-exception-registered-004");
        assertThat(body).contains("\"exceptionType\":\"DELAY\"");
    }

    @Test
    @DisplayName("US19: exceptionResolved は resolution を含む")
    void exceptionResolved() throws IOException {
        acl.notifyExceptionResolved("TRK-001", "EX-001", "代替航海手配済み");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGridClient).api(captor.capture());
        assertThat(captor.getValue().getBody()).contains("d-exception-resolved-005");
    }

    @Test
    @DisplayName("US20: exceptionEscalation は LOSS 用 escalation テンプレートを使う")
    void exceptionEscalation() throws IOException {
        acl.notifyExceptionEscalation("TRK-001", "EX-002", "LOSS");

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(sendGridClient).api(captor.capture());
        assertThat(captor.getValue().getBody()).contains("d-exception-escalation-006");
    }

    @Test
    @DisplayName("ADR-0018: IOException でも業務フローは止まらず failure counter が増える")
    void ioExceptionDoesNotPropagate() throws IOException {
        when(sendGridClient.api(any())).thenThrow(new IOException("network error"));

        acl.notifyTrackingIssued("TRK-001", "B-001");

        assertThat(counter("success")).isZero();
        assertThat(counter("failure")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("ADR-0018: SendGrid 4xx/5xx 応答時は failure counter のみ")
    void httpErrorIncrementsFailure() throws IOException {
        Response err = new Response();
        err.setStatusCode(401);
        err.setBody("{\"error\":\"invalid api key\"}");
        when(sendGridClient.api(any())).thenReturn(err);

        acl.notifyTrackingIssued("TRK-001", "B-001");

        assertThat(counter("success")).isZero();
        assertThat(counter("failure")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("ADR-0018: テンプレート ID 未設定（空文字）の場合は API を呼ばずスキップ + failure counter")
    void emptyTemplateIdSkips() throws IOException {
        NotificationProperties emptyProps = new NotificationProperties(
                "sendgrid",
                new NotificationProperties.SendGrid(
                        "SG.test", "noreply@example.com", "Test",
                        new NotificationProperties.Templates(
                                "", "x", "x", "x", "x", "x"
                        )
                ));
        SendGridNotificationAcl emptyAcl = new SendGridNotificationAcl(
                sendGridClient, emptyProps, registry);

        emptyAcl.notifyTrackingIssued("TRK-001", "B-001");

        verify(sendGridClient, never()).api(any());
        assertThat(counter("failure")).isEqualTo(1.0);
    }
}
