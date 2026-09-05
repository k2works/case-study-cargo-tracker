package com.example.cargotracker.routing.domain.model.events;

import java.time.Instant;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 航海スケジュールを登録した（UC19 / US24）。
 *
 * <p>契約イベントではない（routingms の内側だけで読む）。値は素の型で載せる。
 * 値オブジェクトをそのまま載せると、あとで不変条件を足したときに過去のイベントが
 * 復元できなくなる。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> DCB はイベントに付いたタグで集約を復元する。
 * 付け忘れると集約は毎回<b>空のまま復元され</b>、同一番号の再登録を断る守りが丸ごと
 * 素通りする。それでもテストは緑になる（IT2 で実測）。</p>
 */
public record VoyageRegisteredEvent(
        @EventTag(key = "voyageNumber") String voyageNumber,
        String carrierCode,
        String carrierName,
        String vesselName,
        List<Movement> movements,
        List<String> acceptedCargoTypes,
        String registeredBy) {

    /** 航海内の港間移動。順序は並び順そのもの。 */
    public record Movement(
            String departureUnLocode,
            String arrivalUnLocode,
            Instant departureAt,
            Instant arrivalAt) {
    }
}
