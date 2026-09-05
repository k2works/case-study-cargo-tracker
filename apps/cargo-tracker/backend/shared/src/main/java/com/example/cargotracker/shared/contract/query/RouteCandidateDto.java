package com.example.cargotracker.shared.contract.query;

import java.time.Instant;
import java.util.List;

/**
 * 経路候補 1 件の応答（US08）。
 *
 * <p><b>費用は載せない。</b> 料金表は US21（料金算出・IT13）が正典で、現時点で
 * 存在しない。0 や null を載せると「費用 0 円の経路」と読める。</p>
 *
 * <p><b>routingms の {@code TransitPath} をそのまま写さない。</b> 契約は文字列・数値・
 * 日時だけで組む。</p>
 *
 * @param legs 区間。<b>順序が業務の意味を持つ</b>
 * @param transitDays 所要日数。最初の出発から最後の到着まで（乗り継ぎの待ちを含む）
 * @param direct 直行便か（受入基準 5。並びの根拠を応答にも残す）
 */
public record RouteCandidateDto(
        List<LegDto> legs,
        int transitDays,
        boolean direct) {

    public RouteCandidateDto {
        legs = List.copyOf(legs);
    }

    /**
     * 区間 1 つ。
     *
     * @param voyageNumber 航海番号（受入基準 3）
     */
    public record LegDto(
            String voyageNumber,
            String loadUnLocode,
            String unloadUnLocode,
            Instant loadTime,
            Instant unloadTime) {
    }
}
