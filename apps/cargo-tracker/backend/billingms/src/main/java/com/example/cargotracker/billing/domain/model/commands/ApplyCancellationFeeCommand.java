package com.example.cargotracker.billing.domain.model.commands;

import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.billing.domain.model.valueobjects.TransportRecord;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * キャンセル料を請求に積む（UC22 / US30 §受入基準 9）。
 *
 * <p><b>明細行として積む</b>（正典「キャンセル料の受け皿」）。別の帳票を作らない
 * ——荷主が受け取るものを増やさない。</p>
 *
 * <p><b>輸送実績を運ぶ。</b> キャンセル料は<b>基本料金 × 状態別料率</b>なので、
 * 基本料金を出す材料（区間・重量・貨物種別）が要る。輸送は行われていないが、
 * 「運ぶはずだったもの」の料金が基準になる。</p>
 *
 * @param statusAtCancel キャンセル時の予約の状態。<b>料率はこれで決まる</b>
 *     （{@code cargo.rates.cancellation-fee-rates}）
 */
public record ApplyCancellationFeeCommand(
        @TargetEntityId String invoiceId,
        String bookingId,
        String shipperId,
        String shipperName,
        ShipperType shipperType,
        DiscountRate discountRate,
        String contractNumber,
        TransportRecord transport,
        String statusAtCancel,
        String appliedBy) {
}
