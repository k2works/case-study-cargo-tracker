package com.example.bookingms.domain.commands;

import com.example.bookingms.domain.model.ShipperType;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.math.BigDecimal;

/**
 * 荷主登録コマンド（US02 / US03）。
 *
 * <p>個人荷主（INDIVIDUAL）/ 法人荷主（CORPORATE）共通の登録コマンド。
 * 法人荷主の場合は {@code contractNumber} と {@code discountRate} が必須となり、
 * 個人荷主の場合は両者とも null である必要がある。</p>
 *
 * <p>{@code discountRate} は 0.0 以上 0.3 以下の {@link BigDecimal}
 * （0.000 = 0%, 0.300 = 30%）。</p>
 */
@SuppressWarnings("java:S107") // Axon Command は全荷主属性を必要とするため許容
public record RegisterShipperCommand(
        @TargetAggregateIdentifier String shipperId,
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
