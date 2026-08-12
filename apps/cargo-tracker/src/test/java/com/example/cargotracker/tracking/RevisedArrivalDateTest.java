package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionResolution;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 新しい到着予定日に過去を入れられない（IT12 の C37）。
 *
 * <p>荷役の登録は「未来の出来事は履歴に書かせない」と定めているのに、
 * <strong>例外対応の新しい到着予定日には日付の検査が無かった</strong>
 * （IT11 レビュー C37）。
 *
 * <p><strong>申告日時（C36）とは拒む向きが逆である。</strong> 申告は
 * <strong>起きた事実</strong>なので未来を拒み、新しい到着予定日は
 * <strong>これからの見込み</strong>なので過去を拒む。
 *
 * <p>IT11 のふりかえり T5 の対象である。<strong>時間で守るテストは判別しない</strong>ため、
 * どちらの検査で落ちたのかがメッセージから読める形にする。
 */
@DisplayName("新しい到着予定日（C37）")
class RevisedArrivalDateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, java.time.Month.APRIL, 20);

    /**
     * <strong>過ぎた日を「これから着く日」として荷主に伝えない。</strong>
     *
     * <p>追跡照会の到着予定はここから差し替わる（C18）。過去日を通すと、
     * <strong>着いていない貨物に「昨日着く予定」と表示される</strong>。
     */
    @Test
    void 過去の到着予定日は記録できない() {
        assertThatThrownBy(() -> ExceptionResolution.report(
                "代替便を手配した", TODAY.minusDays(1), TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("過去");
    }

    /** <strong>当日着はありうる。</strong> 境界を「過去」に倒すと本日着が入力できない。 */
    @Test
    void 当日の到着予定日は記録できる() {
        ExceptionResolution resolution =
                ExceptionResolution.report("本日入港した", TODAY, TODAY);

        assertThat(resolution.revisedArrival()).isEqualTo(TODAY);
    }

    /** 到着予定日を伴わない対応は今までどおり記録できる。**任意である。** */
    @Test
    void 到着予定日を伴わない対応は記録できる() {
        ExceptionResolution resolution =
                ExceptionResolution.report("破損の程度を確認中", null, TODAY);

        assertThat(resolution.hasRevisedArrival()).isFalse();
    }

    /**
     * <strong>復元では検査しない。</strong>
     *
     * <p>列が無かったころに解決された例外や、当時は未来だった予定日を持つ行が
     * 読めなくなると、<strong>その貨物の画面ごと 500 になる</strong>
     * （V23 が「読み戻す側は NULL を拒んではならない」と書いた意味）。
     */
    @Test
    void 復元では過去の到着予定日も読み戻せる() {
        ExceptionResolution restored =
                ExceptionResolution.reconstruct("古い対応", TODAY.minusYears(1));

        assertThat(restored.revisedArrival()).isEqualTo(TODAY.minusYears(1));
    }
}
