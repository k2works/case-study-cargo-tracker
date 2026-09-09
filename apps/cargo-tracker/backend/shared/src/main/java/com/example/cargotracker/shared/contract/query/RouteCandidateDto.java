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
 * @param overdueDays 到着期限を何日超えるか（間に合うなら 0。US28 §受入基準 6）。
 *     <b>誤配の再設計（{@code departFromUnLocode} 指定）でだけ 0 より大きくなる</b>——
 *     現在地からでは期限に間に合わないのが普通で、候補を隠すと 0 件になり
 *     貨物が動かせなくなる。経路設計者は超過日数を見て選ぶ
 */
public record RouteCandidateDto(
        List<LegDto> legs,
        int transitDays,
        boolean direct,
        int overdueDays) {

    /** 期限に間に合う候補（通常の設計）。 */
    public RouteCandidateDto(List<LegDto> legs, int transitDays, boolean direct) {
        this(legs, transitDays, direct, 0);
    }

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
