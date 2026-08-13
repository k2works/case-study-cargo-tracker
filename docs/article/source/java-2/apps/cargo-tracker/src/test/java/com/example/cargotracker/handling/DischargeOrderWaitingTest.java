package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.application.internal.queryservices.DischargeOrderView;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 荷降し手配の<strong>緊急度の手がかり</strong>（IT17 の R1）。
 *
 * <p>IT16 の途中レビュー M2：<strong>出ているのは承認日時だけで、いつまでに
 * 降ろすかが読めない</strong>。古い順に並べても、緊急度は分からない。
 *
 * <p><strong>期限そのものはドメインに無い。</strong> キャンセルの承認は
 * 「どこで降ろすか」を決めるが、「いつまでに」は決めていない。無い期限を
 * 画面で作ると、<strong>守れなかったときに誰も責任を負えない数字</strong>になる。
 *
 * <p>そこで<strong>経過を出す</strong>。承認から何日待っているかは事実であり、
 * 貨物が港で滞留するほど保管料と積み替えの手間が増える。
 * <strong>数字の意味を作らず、事実だけを運ぶ。</strong>
 */
@DisplayName("荷降し手配の待ち日数（R1）")
class DischargeOrderWaitingTest {

    private static final Instant 承認 = Instant.parse("2026-04-20T00:00:00Z");

    private static DischargeOrderView 手配(long waitingDays) {
        return new DischargeOrderView(
                "TRK-2026-0001", "JPOSA", "大阪", 承認, "", waitingDays);
    }

    /** <strong>待った日数がそのまま出る。</strong> */
    @Test
    void 待ち日数が読める() {
        assertThat(手配(3).waitingLabel()).isEqualTo("3 日");
    }

    /** <strong>当日は「本日」と出す。</strong> 「0 日」は読み手の手を止める。 */
    @Test
    void 当日は本日と出す() {
        assertThat(手配(0).waitingLabel()).isEqualTo("本日");
    }

    /**
     * <strong>長く待っているものほど目立つ。</strong>
     *
     * <p>色の閾値は{@code ui_design.md} の期限表示に合わせる。
     * <strong>3 日を超えたら注意、7 日を超えたら危険</strong>とする。
     */
    @Test
    void 待つほど強い色になる() {
        assertThat(手配(0).waitingUrgencyClass()).isEqualTo("text-muted");
        assertThat(手配(3).waitingUrgencyClass()).isEqualTo("text-muted");
        assertThat(手配(4).waitingUrgencyClass()).isEqualTo("text-warning");
        assertThat(手配(7).waitingUrgencyClass()).isEqualTo("text-warning");
        assertThat(手配(8).waitingUrgencyClass()).isEqualTo("text-danger");
    }

    /**
     * <strong>長く待っている手配だけを「滞留」と呼ぶ。</strong>
     *
     * <p>画面の出し分けは本述語をそのまま呼ぶ。テンプレートで日数を比べると、
     * <strong>閾値が 2 か所に散る</strong>。
     */
    @Test
    void 滞留の判定は述語が持つ() {
        assertThat(手配(7).stalled()).isFalse();
        assertThat(手配(8).stalled()).isTrue();
    }

    /**
     * <strong>時計より前の承認を負の日数にしない。</strong>
     *
     * <p>時刻のずれや手入力で起こりうる。負の「待ち日数」は読み手を混乱させる。
     */
    @Test
    void 負の待ち日数は本日として扱う() {
        assertThat(手配(-2).waitingLabel()).isEqualTo("本日");
        assertThat(手配(-2).waitingUrgencyClass()).isEqualTo("text-muted");
    }
}
