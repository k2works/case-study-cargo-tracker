package com.example.billingms.infrastructure.outboundservices;

import java.math.BigDecimal;

/**
 * billingms が Invoice 算出のために他コンテキストから集める情報の集合体（ADR-0015）。
 *
 * <p>{@link BillingContextAcl#loadFor(String, String)} の戻り値。
 * 内訳:</p>
 *
 * <ul>
 *   <li>{@code shipperId}: bookingms の Shipper 集約識別子（IT7 では暫定、Task 3.x ApplyDiscount で割引率取得に使う）</li>
 *   <li>{@code weightKg}: 貨物重量（bookingms の CargoSpecification 由来）</li>
 *   <li>{@code cargoType}: 貨物種別 GENERAL / HAZARDOUS / REFRIGERATED（同上）</li>
 *   <li>{@code distanceKm}: 確定旅程の累計距離（routingms の cargo_leg 集計）</li>
 *   <li>{@code originUnlocode} / {@code destinationUnlocode}: 出発港 / 到着港（IT8 で精算書 PDF 用）</li>
 *   <li>{@code handlingCount}: 荷役作業回数（handlingms の handling_activity COUNT）</li>
 *   <li>{@code currency}: 通貨コード（IT7 はデフォルト "JPY"、多通貨対応は IT8 持ち越し）</li>
 * </ul>
 *
 * <p>{@code CrossCargoDeliveredEventHandler} がこれを元に
 * {@link com.example.billingms.domain.model.TransportRecord} を組み立て、
 * {@code CalculateInvoiceCommand} を発火する。</p>
 */
public record BillingContextInfo(
        String shipperId,
        BigDecimal weightKg,
        String cargoType,
        BigDecimal distanceKm,
        String originUnlocode,
        String destinationUnlocode,
        int handlingCount,
        String currency
) {
}
