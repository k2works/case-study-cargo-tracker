package com.example.billingms.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * 課金の対象になる 1 区間（[ADR-027] 決定 1 の改訂）。
 *
 * <p><strong>両端の区分の重いほうを採る。</strong>片端が国内でも、太平洋を渡れば
 * 遠洋の費用がかかる——「東京積み → ロサンゼルス揚げ」を国内として数えると、
 * 距離の代わりにした意味が消える。
 *
 * @param loadRegion 積み地の区分
 * @param unloadRegion 揚げ地の区分
 */
public record ChargeableLeg(PortRegion loadRegion, PortRegion unloadRegion) {

    public ChargeableLeg {
        if (loadRegion == null || unloadRegion == null) {
            throw new IllegalArgumentException("区間の両端の地域区分を指定してください");
        }
    }

    /** 区間係数。**重いほうを採る**ため、向きに依らない。 */
    public BigDecimal factor() {
        return loadRegion.factor().max(unloadRegion.factor());
    }

    /** 画面に出す表示名（重いほうの区分）。 */
    public PortRegion region() {
        return loadRegion.factor().compareTo(unloadRegion.factor()) >= 0
                ? loadRegion : unloadRegion;
    }
}
