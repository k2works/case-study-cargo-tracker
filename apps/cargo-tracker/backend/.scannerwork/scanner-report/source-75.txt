package com.example.cargotracker.booking.domain.model.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 貨物予約を受け付けた（UC03 / US04・US05）。
 *
 * <p>契約イベントではない（bookingms の内側だけで読む）。他サービスが必要とするのは
 * 追跡番号が出たあと（{@code TrackingNumberIssuedEvent}）なので、ここでは
 * {@code shared/contract} に置かない。置くと、読む側の無い契約を先に敷くことになる。</p>
 *
 * <p>値は素の型で載せる。値オブジェクトをそのまま載せると、あとで不変条件を足したとき
 * 過去のイベントが復元できなくなる（新しい検査を古いイベントが通らない）。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> DCB はイベントに付いたタグで集約を復元する。
 * {@code @EventSourced(tagKey)} は集約側の宣言でしかなく、イベント側で「どの項目が
 * そのタグか」を言わないとタグが書かれない。付け忘れると、集約は毎回<b>空のまま
 * 復元され</b>、状態を見る守り（2 度目の受付を断る、状態遷移の検査）が丸ごと
 * 素通りする。それでもテストは緑になる（IT2 で実測）。</p>
 */
public record CargoBookedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String shipperId,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        int quantity,
        String productName,
        String hazardImoClass,
        String hazardUnNumber,
        BigDecimal temperatureMinC,
        BigDecimal temperatureMaxC,
        String bookedBy) {
}
