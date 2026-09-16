package com.example.cargotracker.billing.domain.service;

import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.valueobjects.Money;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;

/**
 * 法人割引（US22）。
 *
 * <p><b>種別で断ち切る。</b> 個人荷主に割引率が入っていることはありうる
 * （登録の誤り、法人から個人への変更）。率だけを見て当てると、その値がそのまま
 * 請求に効いてしまう。</p>
 *
 * <p><b>別のコマンドにしない</b>（計画の注 N10）。割引は算出の中で当てる——
 * 別コマンドにすると、割引の無い請求書が一瞬見える状態が正常系として存在する。</p>
 */
public class DiscountPolicy {

    /** 当てるべき<b>割引額</b>。当たらない種別では 0 円。 */
    public Money discountFor(ShipperType shipperType, DiscountRate rate, Money base) {
        if (!shipperType.discountable()) {
            return Money.zero();
        }
        return rate.appliedTo(base);
    }
}
