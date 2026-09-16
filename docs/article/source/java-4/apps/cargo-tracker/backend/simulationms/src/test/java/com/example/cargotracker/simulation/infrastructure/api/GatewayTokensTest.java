package com.example.cargotracker.simulation.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.simulation.domain.model.valueobjects.StepRole;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * ロールごとのトークンを取る（[ADR-0020] 決定 2）。
 *
 * <p><b>本物の HTTP を通す。</b> モックにすると「ログインの経路を踏む」という
 * 決定そのものが確かめられない——確かめたいのは形ではなく経路である。
 * 相手は {@code HttpServer} の小さなスタブで、外部ライブラリは足さない。</p>
 */
class GatewayTokensTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private RestClient startStub(String body, int status, AtomicInteger calls)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/auth/login", exchange -> {
            calls.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return RestClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();
    }

    @Test
    @DisplayName("ロールごとにログインしてトークンを取る")
    void logsInPerRole() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        GatewayTokens tokens = new GatewayTokens(
                startStub("{\"token\":\"t-1\"}", 200, calls));

        assertThat(tokens.of(StepRole.SALES)).isEqualTo("t-1");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("1 度取ったら使い回す（工程ごとにログインしない）")
    void reusesTheToken() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        GatewayTokens tokens = new GatewayTokens(
                startStub("{\"token\":\"t-1\"}", 200, calls));

        tokens.of(StepRole.SALES);
        tokens.of(StepRole.SALES);

        // **確かめたいもの（業務の連鎖）より認証の負荷が目立つ形にしない。**
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("トークンが返らなければ、何が足りないかを言って止まる")
    void failsLoudlyWhenTheTokenIsMissing() throws IOException {
        GatewayTokens tokens = new GatewayTokens(
                startStub("{}", 200, new AtomicInteger()));

        assertThatThrownBy(() -> tokens.of(StepRole.SALES))
                .isInstanceOf(IllegalStateException.class)
                .as("**「ログインできません」だけでは切り分けられない。**")
                .hasMessageContaining("動作確認用の利用者");
    }
}
