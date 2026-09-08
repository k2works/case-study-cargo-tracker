package com.example.cargotracker.tracking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 例外について荷主へ知らせた（UC16 / US19 §受入基準 3）。
 *
 * <p><b>trackingms の内部イベントであり、契約ではない。</b> bookingms の
 * {@code ShipperNotifiedEvent} は予約（`Cargo`）の内部イベントで、
 * <b>trackingms からは発行も購読もできない</b>（契約のロスターに無い）。
 * 同じ「荷主へ知らせた」でも、知らせた相手も内容も別の集約の事実である。</p>
 *
 * <p><b>送信基盤はスコープ外</b>（ui_design.md:120）。残るのは
 * 「いつ・どうやって・何を伝えたか」で、荷主から「聞いていない」と言われた
 * ときに突き合わせる材料になる。</p>
 */
public record ExceptionShipperNotifiedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String exceptionId,
        String means,
        String summary,
        String notifiedBy,
        Instant notifiedAt) {
}
