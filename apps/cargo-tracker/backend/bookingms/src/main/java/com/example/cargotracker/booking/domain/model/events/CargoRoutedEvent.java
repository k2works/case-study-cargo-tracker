package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予約に経路が決まった（UC07 / US09）。
 *
 * <p>契約イベントではない（bookingms の内側だけで読む）。</p>
 *
 * <p><b>{@code BookingStatus} は動かさない。</b> 経路が付いても、荷主に通知するまでは
 * 提案中である（通知は US12）。動くのは {@code RoutingStatus} だけ。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「引き渡した予約にだけ経路を付ける」という守りが素通りする。</p>
 *
 * @param assignedAt 確定した時刻。投影が現在時刻で決めない（読み直しのたびに動く）
 */
public record CargoRoutedEvent(
        @EventTag(key = "bookingId") String bookingId,
        List<Leg> legs,
        String assignedBy,
        Instant assignedAt) {

    public CargoRoutedEvent {
        legs = List.copyOf(legs);
    }

    /** 区間 1 つ。<b>並び順が業務の意味を持つ。</b> */
    public record Leg(
            String voyageNumber,
            String loadUnLocode,
            String unloadUnLocode,
            Instant loadTime,
            Instant unloadTime) {
    }

    /**
     * 旅程から組み立てる。<b>集約に平坦化の手順を置かない。</b>
     *
     * <p>値オブジェクトを素の型へ写す手順であって、業務の判断ではない。</p>
     */
    public static CargoRoutedEvent of(String bookingId,
            com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary itinerary,
            String assignedBy, java.time.Instant routedAt) {
        return new CargoRoutedEvent(bookingId,
                itinerary.legs().stream()
                        .map(leg -> new Leg(leg.voyageNumber(),
                                leg.load().unLocode().value(),
                                leg.unload().unLocode().value(),
                                leg.loadTime(), leg.unloadTime()))
                        .toList(),
                assignedBy, routedAt);
    }
}
