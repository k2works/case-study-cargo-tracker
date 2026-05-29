package com.example.trackingms.domain.services;

import com.example.trackingms.domain.model.TransportStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 輸送状態遷移ガード（domain-model.md / iteration_plan-5.md US17 / IT5 タスク 2.1）の TDD。
 *
 * <p>{@code TransportStatusTransition.canTransition(from, to)} が
 * 仕様に定義された許可遷移のみを {@code true} で返し、それ以外は {@code false} を返すことを検証する。
 * 状態遷移図は iteration_plan-5.md「輸送状態の遷移（US17 範囲）」に準拠。</p>
 *
 * <ul>
 *   <li>許可：NOT_RECEIVED→RECEIVED→LOADED→IN_TRANSIT→UNLOADED→AWAITING_CLAIM→DELIVERED</li>
 *   <li>許可：UNLOADED→LOADED（中継港の積み替え）</li>
 *   <li>許可：{NOT_RECEIVED, RECEIVED, LOADED, IN_TRANSIT, UNLOADED} → MISROUTED（誤配送検知）</li>
 *   <li>許可：MISROUTED → {RECEIVED, LOADED, IN_TRANSIT}（誤配送からの救済、IT5 H5）</li>
 *   <li>許可：{NOT_RECEIVED..AWAITING_CLAIM} → EXCEPTION（例外発生）</li>
 *   <li>許可：EXCEPTION → {RECEIVED, LOADED, IN_TRANSIT}（復帰）</li>
 *   <li>拒否：同一状態への遷移、終端状態 DELIVERED からの遷移、MISROUTED からの復帰、逆行</li>
 * </ul>
 */
class TransportStatusTransitionTest {

    private final TransportStatusTransition transition = new TransportStatusTransition();

    @ParameterizedTest(name = "[{index}] 許可遷移：{0} → {1}")
    @CsvSource({
            // 正常系の前進遷移
            "NOT_RECEIVED,   RECEIVED",
            "RECEIVED,       LOADED",
            "LOADED,         IN_TRANSIT",
            "IN_TRANSIT,     UNLOADED",
            "UNLOADED,       AWAITING_CLAIM",
            "AWAITING_CLAIM, DELIVERED",
            // 中継港の積み替え
            "UNLOADED,       LOADED",
            // 誤配送検知（5 状態から）
            "NOT_RECEIVED,   MISROUTED",
            "RECEIVED,       MISROUTED",
            "LOADED,         MISROUTED",
            "IN_TRANSIT,     MISROUTED",
            "UNLOADED,       MISROUTED",
            // 例外発生（6 状態から）
            "NOT_RECEIVED,   EXCEPTION",
            "RECEIVED,       EXCEPTION",
            "LOADED,         EXCEPTION",
            "IN_TRANSIT,     EXCEPTION",
            "UNLOADED,       EXCEPTION",
            "AWAITING_CLAIM, EXCEPTION",
            // 例外からの復帰
            "EXCEPTION,      RECEIVED",
            "EXCEPTION,      LOADED",
            "EXCEPTION,      IN_TRANSIT",
            // 誤配送からの救済（IT5 H5）
            "MISROUTED,      RECEIVED",
            "MISROUTED,      LOADED",
            "MISROUTED,      IN_TRANSIT"
    })
    void 許可遷移はtrue(TransportStatus from, TransportStatus to) {
        assertThat(transition.canTransition(from, to)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] 拒否遷移：{0} → {1}")
    @CsvSource({
            // 同一状態への遷移は拒否（更新の意味がない）
            "NOT_RECEIVED,   NOT_RECEIVED",
            "RECEIVED,       RECEIVED",
            "DELIVERED,      DELIVERED",
            // 終端 DELIVERED からの遷移は拒否
            "DELIVERED,      RECEIVED",
            "DELIVERED,      IN_TRANSIT",
            "DELIVERED,      MISROUTED",
            "DELIVERED,      EXCEPTION",
            // MISROUTED から AWAITING_CLAIM / DELIVERED への直接遷移は拒否
            // （正常状態に戻してから順次遷移する設計、IT5 H5）
            "MISROUTED,      MISROUTED",
            "MISROUTED,      NOT_RECEIVED",
            "MISROUTED,      UNLOADED",
            "MISROUTED,      AWAITING_CLAIM",
            "MISROUTED,      DELIVERED",
            "MISROUTED,      EXCEPTION",
            // 逆行（前段階に戻る）は拒否
            "RECEIVED,       NOT_RECEIVED",
            "LOADED,         RECEIVED",
            "IN_TRANSIT,     LOADED",
            "AWAITING_CLAIM, UNLOADED",
            // スキップ（前段階を飛ばす）は拒否
            "NOT_RECEIVED,   LOADED",
            "NOT_RECEIVED,   IN_TRANSIT",
            "RECEIVED,       IN_TRANSIT",
            "LOADED,         UNLOADED",
            // EXCEPTION から AWAITING_CLAIM / DELIVERED / MISROUTED への直接復帰は拒否（IN_TRANSIT 等を経由する）
            "EXCEPTION,      NOT_RECEIVED",
            "EXCEPTION,      UNLOADED",
            "EXCEPTION,      AWAITING_CLAIM",
            "EXCEPTION,      DELIVERED",
            "EXCEPTION,      MISROUTED"
    })
    void 拒否遷移はfalse(TransportStatus from, TransportStatus to) {
        assertThat(transition.canTransition(from, to)).isFalse();
    }

    @Test
    @DisplayName("from が null の場合は IllegalArgumentException")
    void fromがnullの場合は例外() {
        assertThatThrownBy(() -> transition.canTransition(null, TransportStatus.RECEIVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");
    }

    @Test
    @DisplayName("to が null の場合は IllegalArgumentException")
    void toがnullの場合は例外() {
        assertThatThrownBy(() -> transition.canTransition(TransportStatus.NOT_RECEIVED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to");
    }

    /**
     * 全 9×9=81 通りの遷移を網羅して、許可された 24 件以外はすべて拒否されることを保証する
     * （IT5 H5: MISROUTED → {RECEIVED, LOADED, IN_TRANSIT} の救済 3 件追加で 21 → 24）。
     * （仕様変更時に網羅性が失われないようにする保険）。
     */
    @ParameterizedTest(name = "[{index}] {0} → {1} の網羅検証")
    @MethodSource("allTransitions")
    void 全遷移網羅検証(TransportStatus from, TransportStatus to, boolean expectedAllowed) {
        assertThat(transition.canTransition(from, to)).isEqualTo(expectedAllowed);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> allTransitions() {
        java.util.Set<String> allowed = java.util.Set.of(
                "NOT_RECEIVED->RECEIVED",
                "RECEIVED->LOADED",
                "LOADED->IN_TRANSIT",
                "IN_TRANSIT->UNLOADED",
                "UNLOADED->AWAITING_CLAIM",
                "AWAITING_CLAIM->DELIVERED",
                "UNLOADED->LOADED",
                "NOT_RECEIVED->MISROUTED",
                "RECEIVED->MISROUTED",
                "LOADED->MISROUTED",
                "IN_TRANSIT->MISROUTED",
                "UNLOADED->MISROUTED",
                "NOT_RECEIVED->EXCEPTION",
                "RECEIVED->EXCEPTION",
                "LOADED->EXCEPTION",
                "IN_TRANSIT->EXCEPTION",
                "UNLOADED->EXCEPTION",
                "AWAITING_CLAIM->EXCEPTION",
                "EXCEPTION->RECEIVED",
                "EXCEPTION->LOADED",
                "EXCEPTION->IN_TRANSIT",
                // IT5 H5: MISROUTED からの救済
                "MISROUTED->RECEIVED",
                "MISROUTED->LOADED",
                "MISROUTED->IN_TRANSIT"
        );
        Stream.Builder<org.junit.jupiter.params.provider.Arguments> b = Stream.builder();
        for (TransportStatus from : TransportStatus.values()) {
            for (TransportStatus to : TransportStatus.values()) {
                boolean expected = allowed.contains(from.name() + "->" + to.name());
                b.add(org.junit.jupiter.params.provider.Arguments.of(from, to, expected));
            }
        }
        return b.build();
    }
}
