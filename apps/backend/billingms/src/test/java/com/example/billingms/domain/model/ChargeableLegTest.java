package com.example.billingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 課金の対象になる区間（[ADR-027] 決定 1 の改訂）。
 *
 * <p><strong>両端の区分の重いほうを採る。</strong>片端が国内でも、太平洋を渡れば
 * 遠洋の費用がかかる。
 */
@DisplayName("課金対象の区間")
class ChargeableLegTest {

    @Test
    @DisplayName("両端が同じ区分なら、その区分の係数になる")
    void usesTheRegionFactorWhenBothEndsMatch() {
        assertThat(new ChargeableLeg(PortRegion.DOMESTIC, PortRegion.DOMESTIC).factor())
                .isEqualByComparingTo(PortRegion.DOMESTIC.factor());
    }

    /**
     * <strong>重いほうを採る</strong>——そして<strong>向きに依らない</strong>。
     *
     * <p>入れ替えても同じ値になることを対で見る。片方だけを見ると、
     * 「常に出発地の区分を採る」実装でも緑になる。
     */
    @Test
    @DisplayName("両端の区分が違えば、重いほうを採る（向きに依らない）")
    void takesTheHeavierEndRegardlessOfDirection() {
        ChargeableLeg outbound = new ChargeableLeg(PortRegion.DOMESTIC, PortRegion.OCEAN);
        ChargeableLeg inbound = new ChargeableLeg(PortRegion.OCEAN, PortRegion.DOMESTIC);

        assertThat(outbound.factor()).isEqualByComparingTo(PortRegion.OCEAN.factor());
        assertThat(inbound.factor())
                .as("向きで金額が変わっている。往路と復路で違う運賃になる")
                .isEqualByComparingTo(outbound.factor());
    }

    @Test
    @DisplayName("区分の無い区間は断る")
    void rejectsLegsWithoutRegions() {
        assertThatThrownBy(() -> new ChargeableLeg(null, PortRegion.OCEAN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChargeableLeg(PortRegion.OCEAN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
