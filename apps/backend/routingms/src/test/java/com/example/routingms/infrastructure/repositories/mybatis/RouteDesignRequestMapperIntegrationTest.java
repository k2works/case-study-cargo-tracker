package com.example.routingms.infrastructure.repositories.mybatis;

import com.example.routingms.domain.projections.RouteDesignRequestProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RouteDesignRequestMapper（US06 / cross-service 受信 read model）を local-h2 で検証する統合テスト。
 * 各テストは @Transactional でロールバックされ、投入データは相互に分離される。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "axon.axonserver.enabled=false",
                "axon.kafka.publisher.enabled=false",
                "axon.kafka.fetcher.enabled=false"
        }
)
@ActiveProfiles("local-h2")
@Transactional
class RouteDesignRequestMapperIntegrationTest {

    @Autowired
    private RouteDesignRequestMapper mapper;

    @Test
    @DisplayName("経路設計依頼を登録して bookingId で取得できる（status は PENDING）")
    void 登録して取得できる() {
        mapper.insert("B-001", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL");

        RouteDesignRequestProjection found = mapper.findByBookingId("B-001");

        assertThat(found).isNotNull();
        assertThat(found.getOriginUnlocode()).isEqualTo("JPTYO");
        assertThat(found.getDestinationUnlocode()).isEqualTo("USNYC");
        assertThat(found.getArrivalDeadline()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(found.getCargoType()).isEqualTo("GENERAL");
        assertThat(found.getStatus()).isEqualTo("PENDING");
        assertThat(found.getRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("未登録の bookingId は null を返す")
    void 未登録は_null_を返す() {
        assertThat(mapper.findByBookingId("UNKNOWN")).isNull();
    }

    @Test
    @DisplayName("登録済みの経路設計依頼を一覧取得できる")
    void 一覧取得できる() {
        mapper.insert("B-001", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL");
        mapper.insert("B-002", "JPOSA", "SGSIN", LocalDate.of(2026, 10, 15), "REFRIGERATED");

        List<RouteDesignRequestProjection> all = mapper.findAll();

        assertThat(all).extracting(RouteDesignRequestProjection::getBookingId)
                .contains("B-001", "B-002");
    }
}
