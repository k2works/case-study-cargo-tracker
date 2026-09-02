package com.example.simulationms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 継続実行の上限（US37-2・[ADR-031] 決定 3）。
 *
 * <p><strong>上限は「入れたこと」でなく「働くこと」を検証する。</strong>
 * 上限を無視する実装に戻したとき赤くなるかどうかで判定する。
 */
@DisplayName("継続実行の上限")
class ContinuousRunPolicyTest {

    private static final ContinuousRunPolicy POLICY =
            ContinuousRunPolicy.of(30, 3, BigDecimal.valueOf(0.2));

    @Test
    @DisplayName("同時実行数が上限に達したら、新しく開始しない")
    void doesNotStartBeyondTheConcurrencyLimit() {
        assertThat(POLICY.allows(0)).isTrue();
        assertThat(POLICY.allows(2)).isTrue();
        // 3 本走っている状態で 4 本目を始めない
        assertThat(POLICY.allows(3)).isFalse();
        assertThat(POLICY.allows(4)).isFalse();
    }

    @Test
    @DisplayName("設定した値をそのまま読み出せる")
    void keepsTheConfiguredValues() {
        assertThat(POLICY.intervalSeconds()).isEqualTo(30);
        assertThat(POLICY.maxConcurrent()).isEqualTo(3);
        assertThat(POLICY.exceptionRatio()).isEqualByComparingTo(BigDecimal.valueOf(0.2));
    }

    /**
     * <strong>設定を間違えたら、起動しない側に倒す。</strong>間隔 0 は
     * 「休まず実行し続ける」であり、業務を止める側の設定である。
     */
    @Test
    @DisplayName("間隔と同時実行数は 1 以上でなければ断る")
    void rejectsNonPositiveLimits() {
        assertThatThrownBy(() -> ContinuousRunPolicy.of(0, 3, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("間隔");
        assertThatThrownBy(() -> ContinuousRunPolicy.of(30, 0, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同時実行数");
    }

    /**
     * <strong>上限の上限を置く。</strong>置かないと、設定 1 つで業務を止められる
     * ——負荷をかける側を自分で作る以上、自分でクラスタを落とさないことが要る。
     */
    @Test
    @DisplayName("同時実行数には、設定できる上限がある")
    void capsHowHighTheConcurrencyCanBeSet() {
        int beyondLimit = ContinuousRunPolicy.MAX_CONCURRENT_LIMIT + 1;

        assertThatThrownBy(() -> ContinuousRunPolicy.of(30, beyondLimit, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(ContinuousRunPolicy.MAX_CONCURRENT_LIMIT));
    }

    @Test
    @DisplayName("例外の割合は 0 から 1 の間でなければ断る")
    void rejectsARatioOutsideZeroToOne() {
        BigDecimal tooHigh = BigDecimal.valueOf(1.5);

        assertThatThrownBy(() -> ContinuousRunPolicy.of(30, 3, tooHigh))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("例外の割合");
    }
}
