package com.example.cargotracker.billingms.domain.model.services;

import com.example.cargotracker.billingms.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.billingms.domain.model.valueobjects.Money;
import com.example.cargotracker.billingms.domain.model.valueobjects.ShipperType;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * 法人割引ポリシー ドメインサービス（US22）。
 *
 * <p>法人荷主（CORPORATE）には契約割引率を適用し、個人荷主（INDIVIDUAL）には割引なし。
 * 割引率の上限は 30%（CorporateContract の不変条件で保証）。</p>
 */
@Service
public class CorporateDiscountPolicy {

    /**
     * 基本料金に法人割引を適用して割引後金額を返す。
     */
    public Money apply(Money basic, CorporateContract contract) {
        BigDecimal rate = getDiscountRate(contract);
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            return basic;
        }
        Money discount = basic.multiply(rate);
        return basic.subtract(discount);
    }

    /**
     * 契約の割引率を返す。個人荷主は 0。
     */
    public BigDecimal getDiscountRate(CorporateContract contract) {
        if (contract.shipperType() == ShipperType.INDIVIDUAL) {
            return BigDecimal.ZERO;
        }
        return contract.contractedDiscountRate();
    }
}
