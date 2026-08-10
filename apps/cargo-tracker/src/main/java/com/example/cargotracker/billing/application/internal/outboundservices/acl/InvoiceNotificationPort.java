package com.example.cargotracker.billing.application.internal.outboundservices.acl;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 精算書の発行を荷主へ伝えた記録を残す出力ポート（Billing → Booking の ACL。US23）。
 *
 * <p>US23 は「精算書が荷主に<strong>メール通知される</strong>」と述べている。
 * <strong>外部へは送らない</strong>（ADR-006）。残すのは
 * <strong>いつ・誰に・何を伝えたか</strong>であり、
 * 荷主から「請求書が届いていない」と言われたときに答えるための記録である。
 *
 * <p><strong>宛先は Billing が知らない。</strong> 荷主の連絡先は Booking / Shipper の
 * 持ち物である。<strong>Billing に写し取ると、連絡先が変わったときに 2 か所が食い違う</strong>
 * （宛名を凍結する C7 とは目的が違う — あちらは「発行時点の事実」、
 * こちらは「いま届く先」である）。
 */
public interface InvoiceNotificationPort {

    /**
     * 精算書を発行したことを伝えて記録する。
     *
     * <p><strong>できなかったことを例外にしない。</strong> 発行そのものは済んでいる。
     * ここで例外を投げると「発行したのに画面は 500」になる。
     *
     * @param bookingId     予約 ID
     * @param invoiceNumber 精算書番号
     * @param totalAmount   請求金額
     * @param dueDate       支払期限
     * @param actor         操作した人
     * @return 記録できたなら {@code true}（<strong>宛先が無ければ {@code false}</strong>）
     */
    boolean notifyIssued(
            String bookingId, String invoiceNumber, BigDecimal totalAmount,
            LocalDate dueDate, String actor);
}
