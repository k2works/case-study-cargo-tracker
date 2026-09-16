package com.example.cargotracker.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.gateway.infrastructure.config.PublicTrackingRateLimitFilter;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

/**
 * レート制限が<b>実際に配線されている</b>ことを確かめる（US18 / T6b）。
 *
 * <p><b>層が全部緑でも配線は未検査。</b> フィルタの単体テストは、そのフィルタが
 * Gateway に登録されているかを判別しない。登録を忘れても単体は緑のままで、
 * 認証不要経路が無防備になる。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "cargo-tracker.jwt.secret=test-secret-for-gateway-rate-limit-wiring-it",
    // Gateway は Axon を使わないが、起動確認の Bean が接続を要求する。
    "axon.axonserver.enabled=false",
    "cargo-tracker.axon.startup-check.enabled=false",
})
class PublicTrackingRateLimitWiringIT {

    /**
     * 後段の代わりに<b>「見つからない」を返すだけのスタブ</b>を立てる。
     *
     * <p>レート制限は<b>外れだけを数える</b>ので、後段が居ないまま（接続できずに
     * 5xx になる）では何も数えられず、配線が正しくても 429 に到達しない。
     * 総当たりの相手が見るのと同じ応答（404）を返す相手が要る。</p>
     */
    private static final HttpServer TRACKING_STUB = startStub();

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("スタブを立てられませんでした", e);
        }
    }

    @org.springframework.test.context.DynamicPropertySource
    static void routeTrackingToStub(
            org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("TRACKINGMS_URI",
                () -> "http://localhost:" + TRACKING_STUB.getAddress().getPort());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private FilterRegistrationBean<PublicTrackingRateLimitFilter> registration;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    @Test
    @DisplayName("11 回目の公開照会は Gateway が 429 で断る（後段まで届かない）")
    void rejectsTheEleventhRequest() {
        // 後段（trackingms）は起動していない。**429 が返るなら Gateway が断っている**
        // ——通していれば接続できずに 5xx になる。
        String url = "http://localhost:" + port + "/api/v1/tracking/public/TRK-8K2QX7M4RB";

        for (int i = 0; i < 10; i++) {
            rest.get().uri(url).retrieve().toBodilessEntity();
        }

        assertThat(rest.get().uri(url).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("認証より先に通す（断る要求に署名の検証まで走らせない）")
    void runsBeforeAuthentication() {
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
