package com.example.billingms.infrastructure.outboundservices.notification;

import com.example.billingms.config.NotificationProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.sendgrid.SendGrid;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * billingms 用 SendGridNotificationAcl WireMock 統合テスト
 * （IT9 A4.1 / IT8 H1 解消 / US29）。
 *
 * <p>trackingms 版と同様、{@link WireMockCompatibleSendGridClient} で SDK 内部の
 * URL 構築 + HTTP 経路を実 HTTP で検証する。</p>
 */
class SendGridNotificationAclWireMockIT {

    private static final NotificationProperties.Templates TEMPLATES =
            new NotificationProperties.Templates(
                    "d-invoice-issued-001",
                    "d-payment-received-002",
                    "d-overdue-003"
            );

    private static WireMockServer wireMock;
    private static NotificationProperties properties;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        NotificationProperties.SendGrid sg = new NotificationProperties.SendGrid(
                "SG.test-api-key",
                "noreply@cargo-tracker.example.com",
                "Cargo Tracker",
                TEMPLATES
        );
        properties = new NotificationProperties("sendgrid", sg);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void SDK_Client_buildUri_override_経由で請求書発行通知が_WireMockに到達する() {
        wireMock.stubFor(post(urlEqualTo("/v3/mail/send"))
                .willReturn(aResponse().withStatus(202)));

        SendGrid sendGrid = new SendGrid("SG.test-api-key",
                new WireMockCompatibleSendGridClient(wireMock.port()));
        sendGrid.setHost("localhost");
        MeterRegistry registry = new SimpleMeterRegistry();
        SendGridNotificationAcl acl = new SendGridNotificationAcl(sendGrid, properties, registry);

        acl.notifyInvoiceIssued("INV-001", "SHIP-001", "INV-20260820-0001",
                LocalDate.of(2026, 9, 19), new BigDecimal("330000"));

        wireMock.verify(postRequestedFor(urlEqualTo("/v3/mail/send"))
                .withHeader("Authorization", equalTo("Bearer SG.test-api-key"))
                .withRequestBody(matchingJsonPath("$.template_id", equalTo("d-invoice-issued-001"))));
        assertThat(registry.counter("notification.sent",
                "status", "success", "channel", "sendgrid").count()).isEqualTo(1.0);
    }

    @Test
    void WireMockが5xx応答時はfailure_counterが_increment_される() {
        wireMock.stubFor(post(urlEqualTo("/v3/mail/send"))
                .willReturn(aResponse().withStatus(503)));

        SendGrid sendGrid = new SendGrid("SG.test-api-key",
                new WireMockCompatibleSendGridClient(wireMock.port()));
        sendGrid.setHost("localhost");
        MeterRegistry registry = new SimpleMeterRegistry();
        SendGridNotificationAcl acl = new SendGridNotificationAcl(sendGrid, properties, registry);

        acl.notifyOverdue("INV-002", "SHIP-001");

        wireMock.verify(postRequestedFor(urlEqualTo("/v3/mail/send")));
        assertThat(registry.counter("notification.sent",
                "status", "failure", "channel", "sendgrid").count()).isEqualTo(1.0);
    }
}
