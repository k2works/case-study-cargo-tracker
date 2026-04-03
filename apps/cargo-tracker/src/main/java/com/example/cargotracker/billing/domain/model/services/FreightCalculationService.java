package com.example.cargotracker.billing.domain.model.services;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 輸送料金算出ドメインサービス。
 * 重量と貨物種別から基本料金を算出する。
 */
public class FreightCalculationService {

    private static final BigDecimal UNIT_PRICE_GENERAL_CARGO = new BigDecimal("1.0");
    private static final BigDecimal UNIT_PRICE_REFRIGERATED = new BigDecimal("1.5");
    private static final BigDecimal UNIT_PRICE_DANGEROUS_GOODS = new BigDecimal("2.0");

    /**
     * 重量と貨物種別から基本料金を算出する。
     * 結果は HALF_UP で小数点以下 0 桁に丸める。
     *
     * @param weightKg  重量（kg）
     * @param cargoType 貨物種別
     * @return 基本料金
     */
    public BigDecimal calculateBaseAmount(BigDecimal weightKg, CargoType cargoType) {
        if (weightKg == null) throw new IllegalArgumentException("重量は null にできません");
        if (cargoType == null) throw new IllegalArgumentException("貨物種別は null にできません");
        if (weightKg.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("重量は 0 以上でなければなりません");

        BigDecimal unitPrice = unitPricePerKg(cargoType);
        return weightKg.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal unitPricePerKg(CargoType cargoType) {
        return switch (cargoType) {
            case REFRIGERATED -> UNIT_PRICE_REFRIGERATED;
            case DANGEROUS_GOODS -> UNIT_PRICE_DANGEROUS_GOODS;
            default -> UNIT_PRICE_GENERAL_CARGO;
        };
    }
}
