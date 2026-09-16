package com.example.cargotracker.shared.contract.event;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 貨物が荷受人に引き渡された（契約イベント / UC13・UC14）。trackingms → billingms・bookingms。
 *
 * <p><b>配送完了の事実</b>であり、精算処理の開始条件である（US16 §受入基準 4）。
 * 状態を進めるイベント（{@code TransportStatusUpdatedEvent}）とは別に出す——
 * 1 つのイベントに「状態が変わった」と「精算を始めてよい」の 2 つの役割を持たせると、
 * 片方の都合でもう片方の購読側が動くことになる。</p>
 *
 * <p><b>購読側は 2 つ。</b> billingms が精算を始め（{@code BillingReactionHandler}）、
 * bookingms が予約を引取済にする（{@code MarkDeliveredCommand} →
 * {@code BookingDeliveredEvent}）。</p>
 *
 * <p><b>購読側の投影が作れる分を運ぶ。</b> 追跡番号だけでは billingms は請求先を
 * 引けず、bookingms は自分の集約を名指しできない。引き渡した港と時刻は、
 * 請求の根拠として後から問われる（domain-model.md の契約イベント表）。</p>
 */
public record CargoDeliveredEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String bookingId,
        Instant deliveredAt,
        String location) {
}
