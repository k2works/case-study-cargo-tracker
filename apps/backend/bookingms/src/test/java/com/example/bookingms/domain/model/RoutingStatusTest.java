package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 経路の状況（[ADR-026] 決定 2）。
 *
 * <p><strong>この列挙に進行の並びは無い。</strong>判定は
 * {@link RoutingStatus#visibleToRoutingPlanner()} のような<strong>述語</strong>で行っている
 * ——したがって IT8 で {@code BookingStatus} の {@code EXCEPTION} / {@code UNKNOWN} が
 * 「どこへでも進める」実バグを生んだ形は、ここでは構造的に起こらない。
 *
 * <p><strong>それでも、値を足したときに述語が扱いを決め忘れることはありうる。</strong>
 * {@code visibleToRoutingPlanner()} が新しい値を落とすと、
 * <strong>その予約が経路設計者の一覧から消える</strong>——直す人に見えない。
 */
@DisplayName("経路の状況")
class RoutingStatusTest {

    /**
     * <strong>値を足したら、この検査が赤になる。</strong>
     *
     * <p>赤を見た人は「述語がこの値をどう扱うか」を決めることになる。決めずに通ることを
     * 防ぐのが目的であり、値の数を固定することが目的ではない。
     */
    @Test
    @DisplayName("経路の状況は、いま 5 つある")
    void hasTheAgreedValues() {
        assertThat(Arrays.stream(RoutingStatus.values()).map(Enum::name))
                .as("経路の状況が増減した。**新しい値を述語がどう扱うか**を決めること"
                        + "——落とすと、その予約が経路設計者の一覧から消える")
                .containsExactly("NOT_ROUTED", "ROUTING_REQUESTED", "ROUTED",
                        "CONSULTATION_REQUESTED", "MISROUTED");
    }

    /**
     * <strong>誤配の予約は、経路設計者に見える</strong>（[ADR-026] 決定 2・US28-4）。
     *
     * <p>直すのは経路設計者である。一覧から落とすと、**気づいた追跡管理者は連絡先を
     * 探すことになり、直す人は自分の担当だと気づかない**。
     */
    @Test
    @DisplayName("誤配の予約は経路設計者に開く")
    void opensMisroutedCargoToTheRoutingPlanner() {
        assertThat(RoutingStatus.MISROUTED.visibleToRoutingPlanner())
                .as("誤配の予約が経路設計者の一覧から消えている。直す人に見えない")
                .isTrue();
        assertThat(RoutingStatus.openToRoutingPlanner()).contains(RoutingStatus.MISROUTED);
    }

    /** まだ何も始まっていない予約は開かない。**依頼された予約だけを取り出せること**が目的。 */
    @Test
    @DisplayName("まだ依頼されていない予約は開かない")
    void hidesCargoThatWasNeverHandedOver() {
        assertThat(RoutingStatus.NOT_ROUTED.visibleToRoutingPlanner()).isFalse();
    }
}
