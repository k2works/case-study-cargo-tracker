package com.example.bookingms.domain.commands;

import com.example.bookingms.domain.model.ShipperType;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 荷主登録コマンド（US02）。
 *
 * <p>個人荷主 / 法人荷主の共通登録コマンド。法人特有の項目（契約番号・割引率）は
 * US03 で追加する別コマンドで扱う。</p>
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
        String phone
) {}
