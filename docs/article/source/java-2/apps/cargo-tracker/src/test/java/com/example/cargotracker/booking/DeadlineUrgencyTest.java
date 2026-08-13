package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.internal.queryservices.DeadlineUrgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 残り日数の色分け（{@code ui_design.md}）。
 *
 * <p><strong>規則を application 層へ出したから、こう書ける</strong>（ADR-022）。
 * infrastructure に埋まっていたころは、この規則を壊すテストを書く場所が無く、
 * <strong>SQL を実行しないと確かめられなかった</strong>。
 *
 * <p><strong>境界を両側から見る。</strong> 「3 日以内」は 3 を含み 4 を含まない。
 */
@DisplayName("希望期限の残り日数を色に写す（ADR-022）")
class DeadlineUrgencyTest {

    @Test
    void 期限を過ぎたものは赤にする() {
        assertThat(DeadlineUrgency.classOf(-1)).isEqualTo("text-danger fw-bold");
    }

    @Test
    void 三日以内は赤にする() {
        assertThat(DeadlineUrgency.classOf(0)).isEqualTo("text-danger fw-bold");
        assertThat(DeadlineUrgency.classOf(3)).isEqualTo("text-danger fw-bold");
    }

    @Test
    void 四日から七日は橙にする() {
        assertThat(DeadlineUrgency.classOf(4)).isEqualTo("text-warning-emphasis fw-bold");
        assertThat(DeadlineUrgency.classOf(7)).isEqualTo("text-warning-emphasis fw-bold");
    }

    @Test
    void 八日以上は色を付けない() {
        assertThat(DeadlineUrgency.classOf(8)).isEmpty();
        assertThat(DeadlineUrgency.classOf(365)).isEmpty();
    }
}
