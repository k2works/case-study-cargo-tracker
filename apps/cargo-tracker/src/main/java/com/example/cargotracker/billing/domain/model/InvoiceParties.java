package com.example.cargotracker.billing.domain.model;

/**
 * 精算書が指す相手のひと組（US21）。
 *
 * <p><strong>ひと組で持つ。</strong> 3 つはいずれも算出の時点で決まり、
 * <strong>以後は変わらない</strong>。ばらばらに持ち回ると、
 * 予約と荷主が食い違った精算書を作れてしまう。
 *
 * @param invoiceId      精算書番号
 * @param cargoBookingId 予約への参照
 * @param shipperId      荷主への参照（<strong>法人判定を内包する</strong>）
 */
public record InvoiceParties(
        InvoiceId invoiceId, BillingBookingId cargoBookingId, BillingShipperId shipperId) {

    public InvoiceParties {
        if (invoiceId == null || cargoBookingId == null || shipperId == null) {
            throw new IllegalArgumentException("精算書番号・予約 ID・荷主 ID は必須です");
        }
    }
}
