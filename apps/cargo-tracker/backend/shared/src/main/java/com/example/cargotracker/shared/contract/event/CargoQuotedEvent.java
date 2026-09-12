package com.example.cargotracker.shared.contract.event;

import java.math.BigDecimal;
import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予約が見積から作られた（契約イベント / US01・US23 §受入基準 1）。bookingms → billingms。
 *
 * <p><b>概算と請求の差は経理が見る。</b> S61 は「見積ではいくらだったか」と
 * 実際の請求額の差を出す。差が出るのは普通のことで（実際に通った区間は見積の
 * 候補と同じとは限らない）、<b>見なくてよい差と見るべき差を分けるのは人</b>
 * である。出さなければ、その判断そのものができない。</p>
 *
 * <p><b>追跡のイベントには載せない。</b> trackingms は金額に関わらない——
 * そこを通すと、金額を知る必要の無い BC が金額を運ぶことになる。予約の側から
 * 直接伝える。</p>
 *
 * <p><b>見積を経ない予約では、このイベントは出ない。</b> 「概算が無い」は
 * 欠損ではなく普通の状態なので、0 円のイベントで埋めない（0 円の見積があったと
 * 読まれる）。</p>
 *
 * <p><b>{@code @EventTag} は予約 ID に付ける。</b> 送り出す側の集約は
 * {@code Cargo} である。</p>
 *
 * @param quotationId 見積番号。<b>根拠をたどる宛先</b>。金額だけでは、どの見積と
 *     比べているのかを確かめられない
 */
public record CargoQuotedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String quotationId,
        BigDecimal quotedAmount,
        String currency,
        Instant quotedAt) {
}
