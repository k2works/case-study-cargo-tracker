package com.example.billingms.domain.services;

import com.example.billingms.domain.model.CorporateContract;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 法人割引適用ポリシー（US22 / IT7 タスク 3.1、domain-model.md L947-949）。
 *
 * <p>Invoice 集約の {@code ApplyDiscountCommand}（Task 3.3）受理時に呼び出され、
 * 基本料金と荷主契約から割引額を算出する。割引額は {@code invoice_line} の
 * {@code DISCOUNT} 行（負値）として記録される。</p>
 *
 * <p>割引式: {@code discountAmount = basicFee × contract.discountRate}（HALF_UP 丸め、円単位）</p>
 *
 * <ul>
 *   <li>CORPORATE 荷主のみ {@code contract.discountRate} で割引</li>
 *   <li>INDIVIDUAL 荷主は {@code CorporateContract} の不変条件により discountRate=0</li>
 *   <li>結果は HALF_UP 丸めで円単位整数</li>
 * </ul>
 */
@Component
public class CorporateDiscountPolicy {

    /**
     * 基本料金と荷主契約から割引額を算出する。
     *
     * @param basicFee 基本料金（円、0 以上、null 不可）
     * @param contract 荷主契約（null 不可）
     * @return 割引額（円、HALF_UP 丸め、0 以上）
     */
    public BigDecimal calculateDiscount(BigDecimal basicFee, CorporateContract contract) {
        if (basicFee == null || basicFee.signum() < 0) {
            throw new IllegalArgumentException("basicFee は 0 以上の値で必須です: " + basicFee);
        }
        if (contract == null) {
            throw new IllegalArgumentException("contract は必須です");
        }
        return basicFee
                .multiply(contract.discountRate())
                .setScale(0, RoundingMode.HALF_UP);
    }
}
