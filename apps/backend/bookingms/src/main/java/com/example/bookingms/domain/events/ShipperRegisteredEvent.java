package com.example.bookingms.domain.events;

import com.example.bookingms.domain.model.ShipperType;

import java.math.BigDecimal;

/**
 * 荷主登録完了イベント（US02 / US03）。
 *
 * <p>{@link com.example.bookingms.domain.commands.RegisterShipperCommand} の処理成功時に発行され、
 * Read Model の {@code shipper} テーブル更新のトリガーとなる。
 * 法人荷主の場合は {@code contractNumber} / {@code discountRate} が非 null となる。</p>
 */
@SuppressWarnings("java:S107") // Axon Event は全荷主属性を必要とするため許容
public record ShipperRegisteredEvent(
        String shipperId,
        ShipperType shipperType,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String countryCode,
        String postalCode,
        String email,
        String phone,
        String contractNumber,
        BigDecimal discountRate
) {}
