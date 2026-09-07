package com.example.cargotracker.shared.contract.event;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 荷役の記録を取り消した（契約イベント / UC13）。handlingms → trackingms・bookingms。
 *
 * <p><b>元の記録は消えない。</b> 取り消した事実が増えるだけで、履歴には両方が残る。
 * 現場で起きたことを後から無かったことにはしない。</p>
 *
 * <p>購読側は<b>その荷役で進めた分を戻す</b>（trackingms は直前の状態へ、
 * bookingms は最後の荷役の表示を巻き戻す）。</p>
 *
 * <p><b>取り消した内容も運ぶ。</b> 購読側は自分の投影から「何を戻すか」を引き直せる
 * とは限らない（順序が入れ替わることがある）。イベントは購読側の投影が作れる分を運ぶ。</p>
 */
public record HandlingActivityVoidedEvent(
        @EventTag(key = "activityId") String activityId,
        String trackingNumber,
        String bookingId,
        String handlingType,
        String reason,
        String voidedBy,
        Instant voidedAt) {
}
