package com.example.billingms.domain.commands;

import com.example.billingms.domain.model.TransportRecord;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 輸送料金算出コマンド（US21、IT7 タスク 2.3）。
 *
 * <p>{@code CargoDeliveredEvent}（trackingms 集約発火、ADR-0012）を起点に
 * {@code CrossCargoDeliveredEventHandler}（{@code @ProcessingGroup("cross-billing")}、ADR-0015）が
 * 発行する。Invoice 集約を新規生成し、{@link com.example.billingms.domain.services.FareCalculator}
 * で basicAmount を算出して {@link com.example.billingms.domain.events.InvoiceCalculatedEvent} を
 * 発火させる。</p>
 *
 * @param invoiceId  新規発行する Invoice 識別子（UUID 文字列、VARCHAR(36)）
 * @param bookingId  予約識別子（cargoms との集約 ID）
 * @param shipperId  荷主識別子（ApplyDiscountCommand で割引率参照に使う）
 * @param transport  輸送実績（距離・重量・貨物種別・荷役回数・通貨）
 */
public record CalculateInvoiceCommand(
        @TargetAggregateIdentifier String invoiceId,
        String bookingId,
        String shipperId,
        TransportRecord transport
) {
}
