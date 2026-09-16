package com.example.cargotracker.shared.contract.event;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予約がキャンセルされた（契約イベント / UC22・US30）。bookingms → trackingms・handlingms・billingms。
 *
 * <p><b>購読側は 3 つある。</b> trackingms は陸揚げ地を記録し（<b>追跡は閉じない</b>
 * ——閉じるのは当該港の荷降しを受けてから。不変条件 2）、handlingms は貨物の写しに
 * キャンセルの印を付け、billingms はキャンセル料を積む。</p>
 *
 * <p><b>キャンセル時の状態を運ぶ。</b> キャンセル料は状態別の料率で決まるので
 * （正典の料金計算）、billingms は「いつの状態でキャンセルされたか」を知る必要がある。
 * <b>購読側の投影が作れる分を運ぶ</b>——投影はコマンドを読まない。</p>
 *
 * @param trackingNumber 追跡番号。<b>輸送開始前のキャンセルでは {@code null}</b>
 *     （まだ発行されていない）
 * @param dischargeUnLocode 陸揚げ地。<b>輸送中の承認でだけ入る</b>。輸送開始前の
 *     キャンセルは船に載っていないので降ろす港が無い
 */
public record CargoCancelledEvent(
        @EventTag(key = "bookingId") String bookingId,
        String trackingNumber,
        String statusAtCancel,
        String dischargeUnLocode,
        String reason,
        String cancelledBy,
        Instant cancelledAt) {
}
