package com.example.bookingms.infrastructure.repositories.mybatis;

import com.example.bookingms.domain.projections.CargoLeg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CargoLegMapper（US11 確定旅程 cargo_leg）を local-h2 で検証する統合テスト。
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
class CargoLegMapperIntegrationTest {

    @Autowired
    private CargoLegMapper mapper;

    @Test
    @DisplayName("確定旅程を leg_seq 順に登録・取得できる")
    void 旅程を登録して取得できる() {
        mapper.insert("B-001", 1, "V-A", "JPTYO", "SGSIN",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 10, 18, 0));
        mapper.insert("B-001", 2, "V-B", "SGSIN", "DEHAM",
                LocalDateTime.of(2026, 7, 12, 9, 0), LocalDateTime.of(2026, 7, 30, 18, 0));

        List<CargoLeg> legs = mapper.findByBookingId("B-001");

        assertThat(legs).hasSize(2);
        assertThat(legs.get(0).getLegSeq()).isEqualTo(1);
        assertThat(legs.get(0).getVoyageNumber()).isEqualTo("V-A");
        assertThat(legs.get(0).getLoadUnlocode()).isEqualTo("JPTYO");
        assertThat(legs.get(0).getUnloadUnlocode()).isEqualTo("SGSIN");
        assertThat(legs.get(1).getLegSeq()).isEqualTo(2);
        assertThat(legs.get(1).getUnloadUnlocode()).isEqualTo("DEHAM");
    }

    @Test
    @DisplayName("予約 ID 単位で旅程を削除できる（再確定の冪等性）")
    void 旅程を削除して再確定できる() {
        mapper.insert("B-002", 1, "V-OLD", "JPTYO", "USNYC",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0));

        mapper.deleteByBookingId("B-002");
        mapper.insert("B-002", 1, "V-NEW", "JPTYO", "USNYC",
                LocalDateTime.of(2026, 7, 5, 9, 0), LocalDateTime.of(2026, 7, 25, 18, 0));

        List<CargoLeg> legs = mapper.findByBookingId("B-002");
        assertThat(legs).hasSize(1);
        assertThat(legs.get(0).getVoyageNumber()).isEqualTo("V-NEW");
    }
}
