package com.example.cargotracker.tracking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 輸送状態の遷移と呼び名（domain-model.md「TransportStatus 状態遷移」・要素表）。
 *
 * <p><b>正典を写した表をここに持つ。</b> 実装と同じ判定を書き直すと、実装が誤っても
 * 検査が一緒に誤る。この表は設計図（PlantUML）の矢印をそのまま並べたもので、
 * <b>実装からは導いていない</b>。</p>
 *
 * <p><b>全 9 値を回す。</b> 扱っていない値は名乗り出ない（列挙に値を足したとき、
 * 忘れられた場所は赤にならない）。</p>
 */
class TransportStatusTest {

    /** domain-model.md の状態遷移図の矢印をそのまま写したもの。 */
    private static final Map<TransportStatus, Set<TransportStatus>> CANON = Map.of(
            TransportStatus.NOT_RECEIVED,
            EnumSet.of(TransportStatus.RECEIVED, TransportStatus.MISROUTED),
            TransportStatus.RECEIVED,
            EnumSet.of(TransportStatus.LOADED, TransportStatus.MISROUTED,
                    TransportStatus.EXCEPTION),
            TransportStatus.LOADED,
            EnumSet.of(TransportStatus.IN_TRANSIT, TransportStatus.UNLOADED,
                    TransportStatus.AWAITING_CLAIM, TransportStatus.MISROUTED,
                    TransportStatus.EXCEPTION),
            TransportStatus.IN_TRANSIT,
            EnumSet.of(TransportStatus.UNLOADED, TransportStatus.AWAITING_CLAIM,
                    TransportStatus.MISROUTED, TransportStatus.EXCEPTION),
            TransportStatus.UNLOADED,
            EnumSet.of(TransportStatus.LOADED, TransportStatus.MISROUTED,
                    TransportStatus.EXCEPTION),
            TransportStatus.AWAITING_CLAIM,
            EnumSet.of(TransportStatus.DELIVERED, TransportStatus.EXCEPTION),
            // **引取済からは動かない。** 精算の開始条件なので、後から戻ると請求が揺れる。
            TransportStatus.DELIVERED,
            EnumSet.noneOf(TransportStatus.class),
            TransportStatus.MISROUTED,
            EnumSet.of(TransportStatus.LOADED, TransportStatus.UNLOADED),
            // 例外の解決は「例外前の状態へ戻る」。どこへ戻るかは起票前の状態が決める。
            TransportStatus.EXCEPTION,
            EnumSet.of(TransportStatus.RECEIVED, TransportStatus.LOADED,
                    TransportStatus.IN_TRANSIT, TransportStatus.UNLOADED,
                    TransportStatus.AWAITING_CLAIM, TransportStatus.DELIVERED));

    /** 要素表（domain-model.md:123-131）の日本語。 */
    private static final Map<TransportStatus, String> LABELS = Map.of(
            TransportStatus.NOT_RECEIVED, "未受領",
            TransportStatus.RECEIVED, "受領済",
            TransportStatus.LOADED, "積込済",
            TransportStatus.IN_TRANSIT, "輸送中",
            TransportStatus.UNLOADED, "荷降し済",
            TransportStatus.AWAITING_CLAIM, "引取待ち",
            TransportStatus.DELIVERED, "引取済",
            TransportStatus.MISROUTED, "誤配",
            TransportStatus.EXCEPTION, "例外発生");

    @ParameterizedTest
    @EnumSource(TransportStatus.class)
    @DisplayName("不変条件 2: 遷移は正典が許すものだけ（全 9 値 × 全 9 値）")
    void allowsExactlyTheCanonicalTransitions(TransportStatus from) {
        Set<TransportStatus> allowed = CANON.get(from);

        assertThat(allowed).as("正典に %s の行が無い", from).isNotNull();

        for (TransportStatus to : TransportStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s → %s", from, to)
                    .isEqualTo(allowed.contains(to));
        }
    }

    @Test
    @DisplayName("自分自身へは動かない（同じ状態の更新は「変わっていない」）")
    void doesNotTransitionToItself() {
        for (TransportStatus status : TransportStatus.values()) {
            assertThat(status.canTransitionTo(status)).as("%s → %s", status, status).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(TransportStatus.class)
    @DisplayName("呼び名は要素表が正典（画面のバッジもこれに従う）")
    void usesTheCanonicalLabel(TransportStatus status) {
        assertThat(status.label()).isEqualTo(LABELS.get(status));
    }

    @Test
    @DisplayName("例外の対応中は手で動かせる先が無い（押しても断られるボタンを出さない）")
    void allowsNoManualTransitionWhileAnExceptionIsOpen() {
        // 集約は例外中の手動更新を無条件で断る。読み口が canTransitionTo だけで
        // 選択肢を作ると、6 個のボタンが並んでどれを押しても断られる（IT8 レビュー）。
        assertThat(TransportStatus.manualTransitionsFrom(TransportStatus.EXCEPTION)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(TransportStatus.class)
    @DisplayName("手で動かせる先は、集約が受け付ける先と一致する")
    void manualTransitionsMatchWhatTheAggregateAccepts(TransportStatus from) {
        for (TransportStatus to : TransportStatus.manualTransitionsFrom(from)) {
            assertThat(from.canTransitionTo(to)).as("%s → %s", from, to).isTrue();
            assertThat(to.isSetByHand()).as("%s は手で入れられない", to).isTrue();
        }
    }

    // ---- US15 荷役からの導出（IT9） ----

    @Test
    @DisplayName("US15 §4: 荷役の種別から貨物状態が決まる")
    void derivesStatusFromHandling() {
        assertThat(TransportStatus.afterHandling("RECEIVE", false, false))
                .isEqualTo(TransportStatus.RECEIVED);
        assertThat(TransportStatus.afterHandling("LOAD", false, false))
                .isEqualTo(TransportStatus.LOADED);
        assertThat(TransportStatus.afterHandling("CLAIM", true, false))
                .isEqualTo(TransportStatus.DELIVERED);
    }

    @Test
    @DisplayName("同じ荷降しでも、目的港なら引取待ちになる")
    void distinguishesTheFinalPort() {
        // **この判定を集約や購読側に書き直さない。** 書き直すと、片方だけ正しくなる。
        assertThat(TransportStatus.afterHandling("UNLOAD", false, false))
                .isEqualTo(TransportStatus.UNLOADED);
        assertThat(TransportStatus.afterHandling("UNLOAD", true, false))
                .isEqualTo(TransportStatus.AWAITING_CLAIM);
    }

    @Test
    @DisplayName("予定外の荷役は種別によらず誤配（US28 の下地）")
    void treatsOffRouteAsMisrouted() {
        // 予定外に運ばれた貨物は、積んだか降ろしたかより「予定から外れた」ことが重い。
        for (String type : new String[] {"RECEIVE", "LOAD", "UNLOAD", "CLAIM"}) {
            assertThat(TransportStatus.afterHandling(type, false, true))
                    .as("%s（予定外）", type)
                    .isEqualTo(TransportStatus.MISROUTED);
        }
    }

    @Test
    @DisplayName("知らない種別は黙って通さない（契約に値が増えたのに追随していない）")
    void rejectsUnknownHandlingTypes() {
        // 素通りさせると、貨物状態が動かないまま荷役だけが記録される。
        assertThatThrownBy(() -> TransportStatus.afterHandling("CUSTOMS", false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
