package com.example.billingms.application.port;

/**
 * 予約に精算の完了を知らせる（受入基準 23-4・[ADR-028] 決定 1）。
 *
 * <p><strong>これが本 IT で増える結合方向である。</strong>これまで billingms → bookingms は
 * 「料金算出の入力を引く」読み取りだけだった。ここで初めて相手の状態を動かす。
 *
 * <p><strong>`CargoDeliveredEvent` は実装しない</strong>（決定 1）。精算の起点は経理担当者の
 * 操作であり、読む側の無い配線を先に敷かない。こちらは逆向きで、読む側がある
 * ——予約の一覧と詳細が「精算済」を出す。
 */
public interface BookingSettlementNotifier {

    /**
     * 予約を精算済にする。
     *
     * <p><strong>失敗を黙って捨てない。</strong>捨てると、引取済のまま残った予約に
     * 誰も気づけない——例外にしないことは、記録しないことではない。
     */
    void markSettled(String bookingId);
}
