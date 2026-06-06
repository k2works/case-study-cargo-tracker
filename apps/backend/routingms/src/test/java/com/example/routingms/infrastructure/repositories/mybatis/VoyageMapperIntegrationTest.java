package com.example.routingms.infrastructure.repositories.mybatis;

import com.example.routingms.domain.projections.VoyageProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VoyageMapper の検索 SQL（US07）を local-h2 プロファイルで検証する統合テスト。
 * 各テストは @Transactional でロールバックされ、投入データは相互に分離される。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "axon.axonserver.enabled=false",
                "axon.kafka.publisher.enabled=false",
                "axon.kafka.fetcher.enabled=false",
                "app.dev-seed.enabled=false"
        }
)
@ActiveProfiles("local-h2")
@Transactional
class VoyageMapperIntegrationTest {

    @Autowired
    private VoyageMapper voyageMapper;

    private static final LocalDateTime JUN_DEP = LocalDateTime.of(2026, 6, 5, 10, 0);
    private static final LocalDateTime JUN_ARR = LocalDateTime.of(2026, 6, 20, 18, 0);

    private void insert(String voyageNumber, String origin, String dest, LocalDateTime departure) {
        voyageMapper.insertVoyage(voyageNumber, "MOL", "MOL Line", "Ship-" + voyageNumber,
                origin, dest, departure, JUN_ARR);
    }

    @Test
    void 出発地と目的地で検索できる() {
        insert("VS-1", "JPTYO", "USNYC", JUN_DEP);
        insert("VS-2", "JPOSA", "SGSIN", JUN_DEP);

        List<VoyageProjection> result = voyageMapper.search("JPTYO", "USNYC", null, null, null);

        assertThat(result).extracting(VoyageProjection::getVoyageNumber).containsExactly("VS-1");
    }

    @Test
    void 出発期間で絞り込める() {
        insert("VS-3", "JPTYO", "USNYC", LocalDateTime.of(2026, 6, 5, 10, 0));
        insert("VS-4", "JPTYO", "USNYC", LocalDateTime.of(2026, 7, 5, 10, 0));

        List<VoyageProjection> result = voyageMapper.search(null, null,
                LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 6, 30, 23, 59), null);

        assertThat(result).extracting(VoyageProjection::getVoyageNumber).containsExactly("VS-3");
    }

    @Test
    void 危険物指定で対応航海のみに絞り込まれる() {
        insert("VS-5", "JPTYO", "USNYC", JUN_DEP); // 対応種別未登録 = 一般のみ
        insert("VS-6", "JPTYO", "USNYC", JUN_DEP);
        voyageMapper.insertAcceptedCargoType("VS-6", "HAZARDOUS");

        List<VoyageProjection> result = voyageMapper.search(null, null, null, null, "HAZARDOUS");

        assertThat(result).extracting(VoyageProjection::getVoyageNumber)
                .contains("VS-6")
                .doesNotContain("VS-5");
    }

    @Test
    void 一般貨物指定で対応種別未登録の航海も含まれる() {
        insert("VS-7", "JPTYO", "USNYC", JUN_DEP); // 対応種別未登録 = 一般受け入れ

        List<VoyageProjection> result = voyageMapper.search(null, null, null, null, "GENERAL");

        assertThat(result).extracting(VoyageProjection::getVoyageNumber).contains("VS-7");
    }
}
