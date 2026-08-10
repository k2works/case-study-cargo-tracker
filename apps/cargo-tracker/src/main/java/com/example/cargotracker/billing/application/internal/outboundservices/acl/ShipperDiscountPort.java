package com.example.cargotracker.billing.application.internal.outboundservices.acl;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 荷主の<strong>契約</strong>割引率を取得する出力ポート（Billing → Shipper の ACL。US22）。
 *
 * <p><strong>金額から割引率を決めない。</strong> 旧版の
 * {@code DiscountPolicy.calculateRate(shipperType, amount)} は荷主種別と金額から
 * 割引率を算出する設計で、US03 / US22 が要求する「荷主ごとの契約割引率」を
 * <strong>参照する経路そのものが無かった</strong>（設計レビュー H15）。
 *
 * <p>運ぶのは<strong>素の値だけ</strong>である（ADR-005）。Shipper の
 * {@code DiscountRate} を返すと、Billing が Shipper のドメインを参照することになる
 * （ArchUnit ルール 4）。
 */
public interface ShipperDiscountPort {

    /**
     * 契約割引率を引く。
     *
     * @param shipperId 荷主 ID（UUID の文字列表現）
     * @return 契約割引率（0.0000〜0.3000）。<strong>個人荷主・未設定・不明な荷主は空</strong>。
     *         <strong>空は「割引なし」であり、例外にしない</strong> —
     *         そこで止めると請求そのものが止まる
     */
    Optional<BigDecimal> findContractDiscountRate(String shipperId);
}
