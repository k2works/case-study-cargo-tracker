package com.example.cargotracker.billing.domain.model.valueobjects;
import com.example.cargotracker.billing.domain.model.aggregates.InvoiceId;

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
 * @param billed         宛名と追跡番号の写し（<strong>発行時点で凍結する</strong>。C7）
 */
public record InvoiceParties(
        InvoiceId invoiceId, BillingBookingId cargoBookingId, BillingShipperId shipperId,
        BilledParty billed) {

    public InvoiceParties {
        if (invoiceId == null || cargoBookingId == null || shipperId == null) {
            throw new IllegalArgumentException("請求番号・予約 ID・荷主 ID は必須です");
        }
        billed = billed == null ? BilledParty.unknown() : billed;
    }

    /** 宛名を伴わない形（<strong>古い行を読み戻すため</strong>）。 */
    public static InvoiceParties of(
            InvoiceId invoiceId, BillingBookingId cargoBookingId, BillingShipperId shipperId) {
        return new InvoiceParties(invoiceId, cargoBookingId, shipperId, BilledParty.unknown());
    }
}
