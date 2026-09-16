package com.example.cargotracker.handling.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 引取のときに荷受人の確認（署名または確認コード）を取った（US16 §受入基準 1・2）。
 *
 * <p><b>handlingms の内部イベントであり、契約ではない。</b> 荷受人が誰だったかは
 * 引き渡しを証明するための現場の記録で、trackingms も bookingms も billingms も
 * 自分の投影を作るのに要らない——<b>イベントは購読側の投影が作れる分を運ぶ</b>ので、
 * 逆に要らないものを共有カーネルに載せない。</p>
 *
 * <p><b>記録は別のイベントに分ける。</b> 契約
 * {@code HandlingActivityRegisteredEvent} はすでに本番の Event Store にあり、
 * 項目を足すとゴールデンを書き換えることになる（過去のイベントが読めなくなる）。
 * 形を変えずに増やすなら、増やす側を自分の BC に置く。</p>
 */
public record ConsigneeConfirmationRecordedEvent(
        @EventTag(key = "activityId") String activityId,
        String trackingNumber,
        String consigneeName,
        Instant confirmedAt) {
}
