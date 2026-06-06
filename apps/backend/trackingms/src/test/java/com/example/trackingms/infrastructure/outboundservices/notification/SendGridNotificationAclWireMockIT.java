package com.example.trackingms.infrastructure.outboundservices.notification;

import com.example.trackingms.config.NotificationProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.sendgrid.SendGrid;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SendGridNotificationAcl WireMock 統合テスト（IT9 A4.1 / IT8 H1 解消 / US29）。
 *
 * <p>SendGrid SDK の Client.buildUri を override した {@link WireMockCompatibleSendGridClient}
 * を経由して、SDK 内部の URL 構築 + HTTP 経路を実 HTTP で検証する。IT8 では Mockito の
 * Request キャプチャで代替したが、SDK 内部の URL 構築ロジック（{@code URIBuilder.setHost} →
 * port 不可問題）の挙動が検証されていなかった盲点を本テストで解消する。</p>
 */
class SendGridNotificationAclWireMockIT {

    private static final NotificationProperties.Templates TEMPLATES =
            new NotificationProperties.Templates(
                    "d-tracking-issued-001",
                    "d-status-changed-002",
                    "d-misrouted-003",
                    "d-exception-registered-004",
                    "d-exception-resolved-005",
                    "d-exception-escalation-006"
            );

    private static WireMockServer wireMock;
    private static NotificationProperties properties;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        // SendGrid SDK は baseUri=localhost を URIBuilder.setHost に渡し、
        // override で setPort(wireMock.port()) を追加することで動的ポートに到達する
        NotificationProperties.SendGrid sendGridConfig = new NotificationProperties.SendGrid(
                "SG.test-api-key",
                "noreply@cargo-tracker.example.com",
                "Cargo Tracker",
                TEMPLATES
        );
        properties = new NotificationProperties("sendgrid", sendGridConfig);
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
    void SDK_Client_buildUri_override_経由で実HTTP_POSTが_WireMockに到達する() {
        wireMock.stubFor(post(urlEqualTo("/v3/mail/send"))
                .willReturn(aResponse().withStatus(202)));

        SendGrid sendGrid = new SendGrid("SG.test-api-key",
                new WireMockCompatibleSendGridClient(wireMock.port()));
        // SendGrid の host を localhost に固定
        sendGrid.setHost("localhost");
        MeterRegistry registry = new SimpleMeterRegistry();
        SendGridNotificationAcl acl = new SendGridNotificationAcl(sendGrid, properties, registry);

        acl.notifyTrackingIssued("TRK-001", "BK-001");

        wireMock.verify(postRequestedFor(urlEqualTo("/v3/mail/send"))
                .withHeader("Authorization", equalTo("Bearer SG.test-api-key"))
                .withRequestBody(matchingJsonPath("$.template_id", equalTo("d-tracking-issued-001"))));
        assertThat(registry.counter("notification.sent",
                "status", "success", "channel", "sendgrid").count()).isEqualTo(1.0);
    }

    @Test
    void WireMockが5xx応答時はfailure_counterが_increment_される() {
        wireMock.stubFor(post(urlEqualTo("/v3/mail/send"))
                .willReturn(aResponse().withStatus(500)));

        SendGrid sendGrid = new SendGrid("SG.test-api-key",
                new WireMockCompatibleSendGridClient(wireMock.port()));
        sendGrid.setHost("localhost");
        MeterRegistry registry = new SimpleMeterRegistry();
        SendGridNotificationAcl acl = new SendGridNotificationAcl(sendGrid, properties, registry);

        acl.notifyTrackingIssued("TRK-002", "BK-002");

        wireMock.verify(postRequestedFor(urlEqualTo("/v3/mail/send")));
        assertThat(registry.counter("notification.sent",
                "status", "failure", "channel", "sendgrid").count()).isEqualTo(1.0);
    }
}
