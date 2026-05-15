package com.example.cargotracker.bookingms.domain.model.commands;

import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import org.axonframework.modelling.annotation.TargetEntityId;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 見積作成コマンド（US01 / UC01）。
 *
 * <p>受入条件（user_story.md L102-107）:</p>
 * <ol>
 *   <li>出発地・目的地・希望期限・貨物種別・重量を入力できる</li>
 *   <li>航海スケジュール情報をもとにルート概算候補が表示される（候補生成は集約内ロジック）</li>
 *   <li>ルート候補ごとに「経由港・所要日数・概算料金・航海番号」が表示される</li>
 *   <li>見積情報が保存され、見積番号が発行される</li>
 *   <li>希望期限に間に合うルートが存在しない場合、その旨が通知される</li>
 *   <li>危険物が含まれる場合、危険物申告情報の入力フォームが表示される</li>
 * </ol>
 */
public record CreateQuotationCommand(
        @TargetEntityId String quotationId,
        ShipperId shipperId,
        RouteSpecification routeSpec,
        CargoType cargoType,
        BigDecimal weightKg,
        HazardInfo hazardInfo) {

    public CreateQuotationCommand {
        Objects.requireNonNull(quotationId, "quotationId");
        Objects.requireNonNull(shipperId, "shipperId");
        Objects.requireNonNull(routeSpec, "routeSpec");
        Objects.requireNonNull(cargoType, "cargoType");
        Objects.requireNonNull(weightKg, "weightKg");
        if (weightKg.signum() <= 0) {
            throw new IllegalArgumentException("weightKg は正の値である必要があります: " + weightKg);
        }
        if (cargoType == CargoType.HAZARDOUS && hazardInfo == null) {
            throw new IllegalArgumentException("HAZARDOUS の場合、hazardInfo は必須です");
        }
    }
}
