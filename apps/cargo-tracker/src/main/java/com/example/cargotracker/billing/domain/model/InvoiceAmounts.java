package com.example.cargotracker.billing.domain.model;

import java.math.BigDecimal;

/**
 * 精算書が保持する金額のひと組（US21 / US22）。
 *
 * <p><strong>ひと組で持つ。</strong> {@code Invoice} の復元で Checkstyle が
 * パラメータ数の上限で止めた。<strong>制限に当たったのは合図である</strong>
 * （{@code CorrectionRequest.Details} / {@code Cargo.reconstruct} で同じ判断をした）。
 *
 * <p>6 つはいずれも<strong>1 回の算出で同時に決まり、確定後は一緒に動かない</strong>。
 * ばらばらに持ち回ると、片方だけを更新した状態が作れてしまう。
 *
 * <p><strong>丸め後の値である。</strong> 再計算で導出しない。税率もここに持つ —
 * <strong>金額だけでは根拠を再現できない</strong>。
 *
 * @param baseAmount     基本料金（割引適用前）
 * @param discountRate   適用した割引率
 * @param discountAmount 割引額。<strong>US22 の「割引計算の根拠」として保存する</strong>
 * @param taxRate        消費税率。<strong>税制が変わっても発行済みの根拠が再現できる</strong>
 * @param taxAmount      消費税額
 * @param totalAmount    請求総額
 */
public record InvoiceAmounts(
        Money baseAmount,
        DiscountRate discountRate,
        Money discountAmount,
        BigDecimal taxRate,
        Money taxAmount,
        Money totalAmount) {

    public InvoiceAmounts {
        if (baseAmount == null || discountRate == null || discountAmount == null
                || taxAmount == null || totalAmount == null) {
            throw new IllegalArgumentException("請求書の金額は必須です");
        }
        if (taxRate == null || taxRate.signum() < 0) {
            throw new IllegalArgumentException("税率は 0 以上の値が必須です");
        }
    }
}
