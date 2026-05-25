package com.example.bookingms.interfaces.rest.dto;

import com.example.bookingms.domain.projections.ShipperProjection;

import java.math.BigDecimal;

/**
 * 荷主応答 DTO（US02、GET /api/v1/shippers 系）。
 *
 * <p>{@link ShipperProjection} から必要な属性を抽出して返す。
 * 法人特有の {@code contractNumber} / {@code discountRate} は US03 で参照される。</p>
 */
public record ShipperResponse(
        String shipperId,
        String shipperType,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String countryCode,
        String postalCode,
        String email,
        String phone,
        String contractNumber,
        BigDecimal discountRate,
        Boolean active
) {
    public static ShipperResponse from(ShipperProjection p) {
        return new ShipperResponse(
                p.getShipperId(),
                p.getShipperType(),
                p.getName(),
                p.getAddressLine1(),
                p.getAddressLine2(),
                p.getCity(),
                p.getCountryCode(),
                p.getPostalCode(),
                p.getEmail(),
                p.getPhone(),
                p.getContractNumber(),
                p.getDiscountRate(),
                p.getActive()
        );
    }
}
