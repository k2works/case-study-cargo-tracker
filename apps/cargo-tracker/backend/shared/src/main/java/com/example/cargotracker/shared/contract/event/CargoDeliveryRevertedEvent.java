package com.example.cargotracker.shared.contract.event;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 引き渡しの記録が取り消された（契約イベント / UC13・UC14）。trackingms → bookingms・billingms。
 *
 * <p><b>{@code CargoDeliveredEvent} の打ち消し。</b> 引取は荷役の記録なので、
 * 取り違え・二重記録で取り消されることがある。IT10 では取り消し自体を断って
 * 塞いでいた——追跡だけ巻き戻して予約が引取済のままだと、営業には配送完了、
 * 荷役には陸揚げ待ちに見え、<b>BC をまたいだ食い違いが誰にも見えないまま残る</b>。
 * 断るのをやめるには、購読側へ打ち消しを伝える手立てが要る。</p>
 *
 * <p><b>状態を戻すイベント（{@code TransportStatusRevertedEvent}）とは別に出す。</b>
 * 前者は trackingms の内部の話で、こちらは「精算を始めてよいと言ったのを取り消す」
 * という購読側への通知である。1 つに束ねると、状態の巻き戻しのたびに精算が動く。</p>
 *
 * <p><b>取り消しの理由を運ぶ。</b> 予約と請求の履歴に「なぜ引取済でなくなったか」が
 * 残らないと、経理は差し戻しの根拠を後から示せない。</p>
 */
public record CargoDeliveryRevertedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String bookingId,
        Instant revertedAt,
        String reason) {
}
