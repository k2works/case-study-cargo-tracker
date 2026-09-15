package com.example.cargotracker.simulation.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.domain.model.valueobjects.StepRole;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Gateway を人と同じように叩く（[ADR-0020] 決定 2 / IT16 のレビュー N10）。
 *
 * <p><b>本物の HTTP を通す。</b> モックにすると「認可の経路を踏む」という決定
 * そのものが確かめられない。</p>
 */
class GatewayCallsTest {

    private HttpServer server;
    private final List<String> authorizations = new ArrayList<>();
    private final AtomicInteger logins = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
            int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * 業務の経路を 1 本立てる。
     *
     * <p>{@code expiresAfter} 回目までは 401 を返し、そのあと 200 を返す
     * ——<b>期限切れをそのまま再現する</b>（時間では作らない。共有の環境で
     * たまたま成功する形にしない）。</p>
     */
    private GatewayCalls startStub(int expiresAfter) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger businessCalls = new AtomicInteger();
        server.createContext("/api/v1/auth/login", exchange ->
                respond(exchange, 200, "{\"token\":\"t-" + logins.incrementAndGet() + "\"}"));
        server.createContext("/api/v1/booking/bookings", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (businessCalls.incrementAndGet() <= expiresAfter) {
                respond(exchange, 401, "{\"message\":\"トークンの期限が切れています\"}");
            } else {
                respond(exchange, 200, "{\"bookingId\":\"B-1\"}");
            }
        });
        server.start();
        RestClient client = RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();
        return new GatewayCalls(client, new GatewayTokens(client));
    }

    @Test
    @DisplayName("N10: トークンが切れたら取り直して、同じ要求をもう一度送る")
    void renewsTheTokenOnceWhenItExpired() throws IOException {
        GatewayCalls calls = startStub(1);

        GatewayCalls.Response response = calls.get(StepRole.SALES, "/api/v1/booking/bookings");

        assertThat(response.ok())
                .as("US36 の継続実行は何時間も走る。期限切れで工程が止まったと読ませない")
                .isTrue();
        assertThat(logins.get()).as("取り直しは 1 度だけ").isEqualTo(2);
        assertThat(authorizations)
                .as("**取り直した後は新しいトークンで送る**（同じものを送り直さない）")
                .containsExactly("Bearer t-1", "Bearer t-2");
    }

    @Test
    @DisplayName("N10: 取り直しても断られたら、業務上の結果として返す（2 度は試さない）")
    void givesUpAfterOneRenewal() throws IOException {
        GatewayCalls calls = startStub(Integer.MAX_VALUE);

        GatewayCalls.Response response = calls.get(StepRole.SALES, "/api/v1/booking/bookings");

        assertThat(response.status())
                .as("期限ではなく認可の問題である。**握りつぶさず呼び出し元へ届ける**")
                .isEqualTo(401);
        assertThat(logins.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("通った要求ではログインし直さない（認証の負荷を目立たせない）")
    void doesNotRenewOnSuccess() throws IOException {
        GatewayCalls calls = startStub(0);

        calls.get(StepRole.SALES, "/api/v1/booking/bookings");

        assertThat(logins.get()).isEqualTo(1);
    }
}
