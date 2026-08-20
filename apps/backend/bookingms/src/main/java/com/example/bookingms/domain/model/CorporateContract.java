package com.example.bookingms.domain.model;

import java.util.Optional;

/**
 * 法人契約の条件。契約番号と割引率は対で意味を持つ（US03）。
 *
 * <p>契約番号は必須、割引率は任意とする。割引率が未設定なのは「まだ交渉が終わっていない」
 * ことであり、0% ではない。0% にすると設定漏れが「割引なしで合意した契約」として通る。
 *
 * @param number 契約番号
 * @param discountRate 割引率（未設定なら null）
 */
public record CorporateContract(ContractNumber number, DiscountRate discountRate) {

    public CorporateContract {
        if (number == null) {
            throw new IllegalArgumentException("法人荷主には契約番号が必要です");
        }
    }

    /** 割引率。未設定は空を返す。 */
    public Optional<DiscountRate> rate() {
        return Optional.ofNullable(discountRate);
    }
}
