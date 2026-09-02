package com.example.cargotracker.acceptance;

import com.example.cargotracker.booking.BookingApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.axonframework.test.server.AxonServerContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 受け入れテストの土台。bookingms を実際に起動し、API を叩いて確かめる。
 *
 * <p>Axon Server は本番と同じ形（DCB 有効）で立てる。ここを緩めると、
 * 受け入れテストが緑でも本番だけ動かない。</p>
 */
@CucumberContextConfiguration
@SpringBootTest(classes = BookingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {

    static final AxonServerContainer AXON_SERVER =
            new AxonServerContainer("axoniq/axonserver:2026.0.4")
                    .withDevMode(true)
                    .withDcbContext(true)
                    // Axon Server は起動が遅い（設定の初期化だけで数十秒）。開発機が
                    // 混んでいると Testcontainers の既定 60 秒を超えて落ちる。落ちると
                    // 「壊れた」ように見えるが、待てば上がる。k8s の
                    // initialDelaySeconds: 120 と同じ理由で長めに取る。
                    .withStartupTimeout(java.time.Duration.ofMinutes(3));

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
}
