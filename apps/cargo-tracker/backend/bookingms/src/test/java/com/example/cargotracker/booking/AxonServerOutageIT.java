package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.axonframework.test.server.AxonServerContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * デモ項目 5：Axon Server を止めるとコマンドは失敗し、クエリは表示できる。
 *
 * <p>ここで確かめたいのは「無音で in-memory に落ちない」こと。落ちると、登録は
 * 成功したように見えて他のサービスに何も届かず、しかも再起動でイベントが消える。</p>
 *
 * <p>コンテナはこのテスト専用に立てる。共有のものを止めると、他のテストが
 * 巻き添えで落ちて原因が分からなくなる。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AxonServerOutageIT {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    // 組み立ては AbstractAxonIntegrationTest に集める。同じ内容を各テストで書くと、
    // 起動猶予のような設定を片方だけ直すことになる（IT2 で実際に起きた）。
    // このテストは停止と再開を自分で操るので、基底クラスは継承せず組み立てだけ借りる。
    static final AxonServerContainer AXON_SERVER =
            com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest
                    .axonServerContainer();

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        AXON_SERVER.start();
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("axon.axonserver.servers", AXON_SERVER::getAxonServerAddress);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * 止めたコンテナは必ず戻す。
     *
     * <p>戻さないと、同じ JVM で後から動くテストが「たまに」落ちる。落ちるのは
     * 実行順で変わるので、原因がこのテストにあることに気づきにくい。</p>
     */
    @AfterAll
    static void restartAxonServer() {
        if (!AXON_SERVER.isRunning()) {
            AXON_SERVER.start();
        }
    }

    @LocalServerPort
    private int port;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private ResponseEntity<JsonMap> register(String email) {
        return rest.post().uri("http://localhost:" + port + "/api/v1/booking/shippers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "山田商事", "shipperType", "CORPORATE", "email", email,
                        "phone", "03-0000-0000", "address", "東京都中央区",
                        "contractNumber", "CT-0001", "discountRate", "0.1000"))
                .retrieve().toEntity(JsonMap.class);
    }

    private ResponseEntity<JsonMap> list() {
        return rest.get().uri("http://localhost:" + port + "/api/v1/booking/shippers?page=0&size=50")
                .retrieve().toEntity(JsonMap.class);
    }

    @Test
    @Order(1)
    @DisplayName("Axon Server が動いていれば登録できる")
    void registersWhileAxonServerIsUp() {
        assertThat(register("outage-before-" + System.nanoTime() + "@example.com").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @Order(2)
    @DisplayName("Axon Server を止めるとコマンドは失敗するが、一覧は表示できる")
    void commandFailsButQueryStillWorks() {
        AXON_SERVER.stop();
        try {
            ResponseEntity<JsonMap> created =
                    register("outage-during-" + System.nanoTime() + "@example.com");

            assertThat(created.getStatusCode().is2xxSuccessful())
                    .as("Axon Server が居ないのに成功したら、無音で in-memory に落ちている。"
                            + "登録できたように見えて他のサービスに何も届かない")
                    .isFalse();

            // 一覧は投影テーブル（PostgreSQL）だけを読むので、Axon Server が居なくても出る。
            assertThat(list().getStatusCode())
                    .as("読めるものまで止めると、障害時に業務が完全に止まる")
                    .isEqualTo(HttpStatus.OK);
        } finally {
            // 後始末はしない。コンテナはこのクラス専用で、@DirtiesContext で片づく。
            assertThat(AXON_SERVER.isRunning()).isFalse();
        }
    }
}
