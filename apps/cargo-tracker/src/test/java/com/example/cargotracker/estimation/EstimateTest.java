package com.example.cargotracker.estimation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.estimation.domain.model.aggregates.Estimate;
import com.example.cargotracker.estimation.domain.model.valueobjects.EstimateStatus;
import com.example.cargotracker.estimation.domain.model.valueobjects.EstimationCargoType;
import com.example.cargotracker.estimation.domain.model.entities.RouteCandidate;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 見積の不変条件（US01）。
 *
 * <p>正典は {@code domain-model.md} の「7. Estimation Context」ビジネスルール。
 *
 * <p><strong>画面からの受け入れテストは集約の守りを判別しない。</strong>
 * 画面で先に弾かれる条件は、集約を直接壊しにいかないと確かめられない
 * （IT8 の教訓）。ここでは集約そのものを壊す。
 */
@DisplayName("US01 見積の不変条件")
class EstimateTest {

    private static final Location 大阪 = Location.of("JPOSA");
    private static final Location ロサンゼルス = Location.of("USLAX");
    private static final LocalDate 期限 = LocalDate.of(2026, java.time.Month.OCTOBER, 1);

    private static Estimate 見積(Location origin, Location destination, BigDecimal weightKg) {
        return Estimate.create(
                origin, destination, 期限, EstimationCargoType.GENERAL, weightKg, null);
    }

    /** <strong>作った直後は作成済である</strong>（ビジネスルール 5）。 */
    @Test
    void 作成した見積は作成済である() {
        Estimate estimate = 見積(大阪, ロサンゼルス, new BigDecimal("1000"));

        assertThat(estimate.statusAsOf(期限.minusDays(1))).isEqualTo(EstimateStatus.CREATED);
        assertThat(estimate.estimateId()).isNotNull();
        assertThat(estimate.candidates()).isEmpty();
    }

    /**
     * <strong>期限を過ぎた見積は期限切れである</strong>（ビジネスルール 7）。
     *
     * <p><strong>期限当日は期限切れにしない。</strong> 当日着の便はまだ間に合う
     * （ADR-019 と同じ境界）。
     */
    @Test
    void 期限を過ぎた見積は期限切れである() {
        Estimate estimate = 見積(大阪, ロサンゼルス, new BigDecimal("1000"));

        assertThat(estimate.statusAsOf(期限)).as("当日はまだ間に合う").isEqualTo(EstimateStatus.CREATED);
        assertThat(estimate.statusAsOf(期限.plusDays(1))).isEqualTo(EstimateStatus.EXPIRED);
    }

    /**
     * <strong>出発地と目的地が同じ見積は作れない</strong>（ビジネスルール 2）。
     *
     * <p>同一地点への輸送は業務として成り立たない。
     */
    @Test
    void 出発地と目的地が同じでは作れない() {
        assertThatThrownBy(() -> 見積(大阪, 大阪, new BigDecimal("1000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発地と目的地");
    }

    /** <strong>重量は正の値である</strong>（ビジネスルール 3）。 */
    @Test
    void 重量が正でなければ作れない() {
        assertThatThrownBy(() -> 見積(大阪, ロサンゼルス, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重量");
        assertThatThrownBy(() -> 見積(大阪, ロサンゼルス, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重量");
    }

    /**
     * <strong>候補を差し替えられる</strong>（ビジネスルール 6。ADR-023）。
     *
     * <p>Routing の探索から得た候補を集約に入れる。
     */
    @Test
    void ルート候補を差し替えられる() {
        Estimate estimate = 見積(大阪, ロサンゼルス, new BigDecimal("1000"));

        estimate.replaceCandidates(List.of(候補("V0001", 18, 120000)));

        assertThat(estimate.candidates()).hasSize(1);
        assertThat(estimate.candidates().getFirst().voyageNumber()).isEqualTo("V0001");
    }

    /**
     * <strong>候補を外から差し替えられない。</strong>
     *
     * <p>渡した後のリストを書き換えられると、<strong>画面に出す候補と
     * 保存した候補がずれる</strong>。
     */
    @Test
    void 渡した候補のリストを後から書き換えられない() {
        Estimate estimate = 見積(大阪, ロサンゼルス, new BigDecimal("1000"));
        List<RouteCandidate> mutable = new java.util.ArrayList<>(List.of(候補("V0001", 18, 120000)));

        estimate.replaceCandidates(mutable);
        mutable.clear();

        assertThat(estimate.candidates()).hasSize(1);
    }

    /** <strong>候補の中身も検証する</strong>（ビジネスルール 4）。 */
    @Test
    void 候補の値が不正なら作れない() {
        assertThatThrownBy(() -> 候補("", 18, 120000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 候補("V0001", 0, 120000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> 候補("V0001", 18, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RouteCandidate 候補(String voyageNumber, int transitDays, int cost) {
        return new RouteCandidate(
                voyageNumber, "JPTYO", transitDays, BigDecimal.valueOf(cost), "JPY");
    }
}
