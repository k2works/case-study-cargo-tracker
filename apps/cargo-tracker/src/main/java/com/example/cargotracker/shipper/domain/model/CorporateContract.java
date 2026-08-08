package com.example.cargotracker.shipper.domain.model;

/**
 * 法人契約（US03）。契約番号と契約割引率の<strong>ひと組</strong>。
 *
 * <p>別々に持つと、成り立たない組み合わせを作れる。
 *
 * <ul>
 *   <li>割引率はあるが契約番号が無い（<strong>割引の根拠を請求書に書けない</strong>）</li>
 *   <li>契約番号はあるが割引率が無い（割引 0% なのか未設定なのか分からない）</li>
 * </ul>
 *
 * <p><strong>{@code domain-model.md} は {@code CorporateShipper} を
 * {@code Shipper} のサブタイプとして定義していたが、本 IT で判断を変えた</strong>
 * （IT7 設計反映 #12）。{@code Shipper} は {@code final} かつ不変であり、
 * 継承すると「法人なのに契約が無い」「個人なのに契約がある」組み合わせを
 * 型で防げなくなる。<strong>値としてひと組で持つほうが、不正な状態を作れない。</strong>
 *
 * @param contractNumber 契約番号
 * @param discountRate   契約割引率
 */
public record CorporateContract(ContractNumber contractNumber, DiscountRate discountRate) {

    public CorporateContract {
        if (contractNumber == null) {
            throw new IllegalArgumentException("契約番号は必須です");
        }
        if (discountRate == null) {
            throw new IllegalArgumentException("契約割引率は必須です");
        }
    }
}
