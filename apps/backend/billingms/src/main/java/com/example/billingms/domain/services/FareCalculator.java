package com.example.billingms.domain.services;

import com.example.billingms.domain.model.RateTable;
import com.example.billingms.domain.model.TransportRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 基本料金計算ドメインサービス（US21、domain-model.md L922-925、iteration_plan-7 §設計）。
 *
 * <p>輸送実績（{@link TransportRecord}）と料金単価表（{@link RateTable}）から
 * 基本料金を算出する。Invoice 集約が {@code CalculateInvoiceCommand} 受理時に
 * 本サービスを呼び出して basicAmount を確定する（Task 2.3）。</p>
 *
 * <p>計算式（iteration_plan-7 §設計 と S20 UI サンプル値で検証）:</p>
 *
 * <pre>
 *   basicFee = (weightKg × distanceKm × cargoTypeFactor) + (handlingCount × handlingUnitFee)
 * </pre>
 *
 * <p>S20 サンプル: 1200kg × 5300km × 0.05 + 8 回 × 1500 円 = 318,000 + 12,000 = 330,000 円。
 * 結果は円単位（小数第 0 位、HALF_UP 丸め）。BigDecimal の精度は 2 桁を維持し、最終結果のみ
 * 整数に丸める。</p>
 */
@Component
public class FareCalculator {

    private final RateTable rateTable;

    public FareCalculator(RateTable rateTable) {
        if (rateTable == null) {
            throw new IllegalArgumentException("rateTable は必須です");
        }
        this.rateTable = rateTable;
    }

    /**
     * 輸送実績から基本料金を算出する。
     *
     * @param transport 輸送実績
     * @return 基本料金（円単位、HALF_UP 丸め）
     */
    public BigDecimal calculate(TransportRecord transport) {
        if (transport == null) {
            throw new IllegalArgumentException("transport は必須です");
        }
        BigDecimal cargoTypeFactor = rateTable.cargoTypeFactor(transport.cargoType());

        BigDecimal distanceFee = transport.weightKg()
                .multiply(transport.distanceKm())
                .multiply(cargoTypeFactor);

        BigDecimal handlingFee = rateTable.handlingUnitFee()
                .multiply(BigDecimal.valueOf(transport.handlingCount()));

        return distanceFee.add(handlingFee).setScale(0, RoundingMode.HALF_UP);
    }
}
