package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 追跡番号を発行した（UC12 / US14）。
 *
 * <p><b>ここから連鎖が始まる。</b> {@code BookingReactionHandler} がこれを受けて
 * trackingms へ {@code InitializeTrackingCommand} を送る。</p>
 *
 * <p><b>{@code legs} を落とさない。</b> 購読側（handlingms の {@code CargoSnapshot}・
 * IT9）がまだ無くても載せる。契約イベントは追記専用で、あとから形を変えられない。
 * 「いま要らないから」で落とすと、IT9 で契約を変えることになる。</p>
 *
 * <p><b>列挙型を載せない。</b> {@code cargoType} は文字列で運ぶ。同じ名前でも
 * BC ごとに値と意味が違う（domain-model.md「置かないもの」）。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「二重に発行しない」守り（不変条件 8）が素通りする。</p>
 */
public record TrackingNumberIssuedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String trackingNumber,
        String origin,
        String destination,
        String cargoType,
        List<Leg> legs,
        String issuedBy,
        Instant issuedAt) {

    /** 旅程の 1 区間。積む順に並ぶ。 */
    public record Leg(
            String voyageNumber,
            String loadUnLocode,
            String unloadUnLocode,
            Instant loadTime,
            Instant unloadTime) {
    }

    /** 確定済みの旅程から組み立てる。<b>集約に平坦化の手順を置かない。</b> */
    public static TrackingNumberIssuedEvent of(String bookingId, String trackingNumber,
            String origin, String destination, String cargoType,
            List<CargoRoutedEvent.Leg> routedLegs, String issuedBy, Instant issuedAt) {
        return new TrackingNumberIssuedEvent(bookingId, trackingNumber, origin, destination,
                cargoType,
                routedLegs.stream().map(leg -> new Leg(leg.voyageNumber(),
                        leg.loadUnLocode(), leg.unloadUnLocode(),
                        leg.loadTime(), leg.unloadTime())).toList(),
                issuedBy, issuedAt);
    }
}
