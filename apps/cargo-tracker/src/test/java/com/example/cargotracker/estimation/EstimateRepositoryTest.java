package com.example.cargotracker.estimation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.estimation.domain.model.aggregates.Estimate;
import com.example.cargotracker.estimation.domain.model.valueobjects.EstimationCargoType;
import com.example.cargotracker.estimation.domain.model.entities.RouteCandidate;
import com.example.cargotracker.estimation.domain.repository.EstimateRepository;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 見積の永続化（US01）。
 *
 * <p><strong>SQL の正しさを検証する唯一の場所は実 PostgreSQL である</strong>（ADR-003）。
 */
@DisplayName("見積の永続化（US01）")
class EstimateRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private EstimateRepository repository;

    private static Estimate 見積() {
        return Estimate.create(
                Location.of("JPOSA"), Location.of("USLAX"),
                LocalDate.of(2026, java.time.Month.OCTOBER, 1), EstimationCargoType.GENERAL,
                new BigDecimal("1000.000"), null);
    }

    /** <strong>保存した見積を見積番号で読み戻せる</strong>（受入基準 4）。 */
    @Test
    void 保存した見積を読み戻せる() {
        Estimate estimate = 見積();
        repository.save(estimate);

        Estimate found = repository.findByEstimateId(estimate.estimateId()).orElseThrow();

        assertThat(found.origin()).isEqualTo(Location.of("JPOSA"));
        assertThat(found.destination()).isEqualTo(Location.of("USLAX"));
        assertThat(found.cargoType()).isEqualTo(EstimationCargoType.GENERAL);
        assertThat(found.weightKg()).isEqualByComparingTo("1000.000");
        assertThat(found.arrivalDeadline()).isEqualTo(LocalDate.of(2026, java.time.Month.OCTOBER, 1));
    }

    /**
     * <strong>ルート候補も一緒に読み戻せる</strong>（受入基準 3）。
     *
     * <p><strong>候補が消えると、見積の意味が無くなる。</strong>
     */
    @Test
    void ルート候補も読み戻せる() {
        Estimate estimate = 見積();
        estimate.replaceCandidates(List.of(
                new RouteCandidate("V0001", "JPTYO", 18, new BigDecimal("120000"), "JPY"),
                new RouteCandidate("V0002", null, 21, new BigDecimal("98000"), "JPY")));
        repository.save(estimate);

        Estimate found = repository.findByEstimateId(estimate.estimateId()).orElseThrow();

        assertThat(found.candidates()).hasSize(2);
        assertThat(found.candidates())
                .extracting(RouteCandidate::voyageNumber)
                .containsExactly("V0001", "V0002");
        assertThat(found.candidates().get(1).isDirect())
                .as("経由港が無ければ直行である")
                .isTrue();
    }

    /** <strong>見つからない見積は空で返る。</strong> 例外にしない。 */
    @Test
    void 見つからない見積は空である() {
        assertThat(repository.findByEstimateId(
                com.example.cargotracker.estimation.domain.model.aggregates.EstimateId.generate()))
                .isEmpty();
    }

    /**
     * <strong>保存すると版が進む</strong>（IT15 の C4）。
     *
     * <p>版を進めてよいのは、保存が成功したことを知っているリポジトリだけである。
     */
    @Test
    void 保存すると版が進む() {
        Estimate estimate = 見積();
        assertThat(estimate.version()).isZero();

        repository.save(estimate);

        assertThat(estimate.version()).isEqualTo(1L);
    }
}
