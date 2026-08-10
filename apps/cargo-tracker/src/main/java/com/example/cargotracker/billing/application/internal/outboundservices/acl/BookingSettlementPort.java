package com.example.cargotracker.billing.application.internal.outboundservices.acl;

/**
 * 予約を精算済みにする出力ポート（Billing → Booking の ACL。US23）。
 *
 * <p>US23 の受入基準は「入金確認後、精算状態が『精算済』に更新され
 * <strong>予約状態も『精算済』になる</strong>」である。
 * <strong>Billing が Booking のテーブルを直接書かない</strong>（ADR-012 / ADR-015）。
 * 遷移してよいかを判断するのは予約自身である（遷移表 #8）。
 *
 * <p><strong>{@code SETTLED} は終端状態である。</strong> 以後いかなるコマンドも
 * 受け付けない — 引取記録の訂正・取り消し（US36）もここで止まる。
 * だからこそ<strong>入金を確認できたときにだけ</strong>呼ぶ。
 */
public interface BookingSettlementPort {

    /**
     * 予約を精算済みにする。
     *
     * <p><strong>できなかったことを例外にしない。</strong> 予約が見つからない・
     * すでに精算済み・他の更新が先行した、はいずれも起こりうる。
     * 入金の記録そのものは済んでいるため、<strong>ここで例外を投げると
     * 「入金は記録したのに画面は 500」になる</strong>。
     *
     * @param bookingId 予約 ID
     * @return 精算済みにできたなら {@code true}
     */
    boolean settle(String bookingId);
}
