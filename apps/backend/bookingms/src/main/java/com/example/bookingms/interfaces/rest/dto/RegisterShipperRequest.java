package com.example.bookingms.interfaces.rest.dto;

import com.example.bookingms.domain.model.ShipperType;

import java.math.BigDecimal;

/**
 * 荷主登録リクエスト DTO（US02 / US03、POST /api/v1/shippers）。
 *
 * <p>{@code shipperId} は省略可能で、省略時はサーバー側で UUID を採番する。
 * 法人荷主の場合は {@code contractNumber} / {@code discountRate} が必須となる
 * （0.0-0.3 の {@link BigDecimal}）。</p>
 */
public record RegisterShipperRequest(
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
