package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.domain.repository.EstimateRepository;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.aggregates.Estimate;
import com.example.bookingms.domain.model.valueobjects.EstimateId;
import com.example.bookingms.domain.model.valueobjects.EstimateStatus;
import com.example.bookingms.domain.model.valueobjects.RouteCandidate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 見積の永続化（US01-4）。
 *
 * <p><strong>候補ごと読み戻せることを見る。</strong>見積だけ保存して候補を落とすと、
 * 開き直したときに「候補が 0 件の見積」になる——荷主に出した数字が消える。
 */
@DisplayName("見積の永続化")
class EstimatePersistenceIntegrationTest extends CargoPersistenceTestBase {

    @Autowired
    private EstimateRepository estimates;

    private Estimate estimate(List<RouteCandidate> candidates) {
        return Estimate.create(EstimateId.generate(), estimates.nextNumber(),
                new com.example.bookingms.domain.model.valueobjects.EstimateRequirements(
                        "JPTYO", "USLAX", LocalDate.parse("2027-12-31"), CargoType.GENERAL,
                        new BigDecimal("4200.000")),
                candidates);
    }

    /**
     * <strong>丸ごと比べる。</strong>項目ごとの比較の積み上げは、属性が増えるたび漏れる。
     */
    @Test
    @DisplayName("見積を候補ごと読み戻せる")
    void restoresTheEstimateWithItsCandidates() {
        Estimate created = estimate(List.of(
                new RouteCandidate("V001", null, 12, new BigDecimal("300000.00")),
                new RouteCandidate("V002", "SGSIN", 21, new BigDecimal("420000.00"))));
        estimates.save(created);

        Estimate restored = estimates.findById(created.estimateId().value().toString())
                .orElseThrow();

        assertThat(restored.estimateNumber()).isEqualTo(created.estimateNumber());
        assertThat(restored.originUnLocode()).isEqualTo("JPTYO");
        assertThat(restored.destinationUnLocode()).isEqualTo("USLAX");
        assertThat(restored.arrivalDeadline()).isEqualTo(LocalDate.parse("2027-12-31"));
        assertThat(restored.cargoType()).isEqualTo(CargoType.GENERAL);
        assertThat(restored.weightKg()).isEqualByComparingTo("4200");
        assertThat(restored.status()).isEqualTo(EstimateStatus.CREATED);
        // **順序ごと戻る。**推奨順に意味がある（上から見せる）
        assertThat(restored.candidates())
                .as("ルート候補が戻っていない。荷主に出した数字が消える")
                .containsExactlyElementsOf(created.candidates());
    }

    /** 候補が 1 件も無い見積も読み戻せる（受入基準 01-5）。 */
    @Test
    @DisplayName("候補の無い見積も読み戻せる")
    void restoresAnEstimateWithoutCandidates() {
        Estimate created = estimate(List.of());
        estimates.save(created);

        assertThat(estimates.findById(created.estimateId().value().toString()).orElseThrow()
                .candidates()).isEmpty();
    }

    /** 荷主が電話で読み上げた番号で探せる（受入基準 01-4）。 */
    @Test
    @DisplayName("見積番号から引ける")
    void findsByEstimateNumber() {
        Estimate created = estimate(List.of());
        estimates.save(created);

        assertThat(estimates.findByNumber(created.estimateNumber().value()).orElseThrow()
                .estimateId()).isEqualTo(created.estimateId());
    }

    /**
     * <strong>採番は DB のシーケンスに任せる</strong>（[ADR-011] と同じ形）。
     *
     * <p>MAX+1 の自前採番は、同時に 2 件作られたときに衝突する。
     */
    @Test
    @DisplayName("見積番号は EST-YYYY と 6 桁で、重複しない")
    void numbersAreUniqueAndWellFormed() {
        String first = estimates.nextNumber().value();
        String second = estimates.nextNumber().value();

        assertThat(first).matches("^EST-\\d{10}$");
        assertThat(second).isNotEqualTo(first);
    }

    /** **新しい順に並ぶ**——直近に作ったものから見る。 */
    @Test
    @DisplayName("一覧は新しい順に並ぶ")
    void listsTheNewestFirst() {
        Estimate older = estimate(List.of());
        estimates.save(older);
        Estimate newer = estimate(List.of());
        estimates.save(newer);

        assertThat(estimates.findAll().stream()
                .map(candidate -> candidate.estimateId().value().toString()).toList())
                .startsWith(newer.estimateId().value().toString());
    }
}
