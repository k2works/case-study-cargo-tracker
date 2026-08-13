package com.example.cargotracker.billing.application.internal.outboundservices.acl;

import java.util.Optional;

/**
 * 荷主の連絡先を読む出力ポート（Billing → Shipper の ACL。IT14 レビュー C3）。
 *
 * <p><strong>督促は「気づくこと」で終わらない。</strong> 支払期限を過ぎた請求書に
 * 気づいても、そこから相手に連絡できなければ仕事は進まない
 * （「気づく手段は次の行動へ繋ぐ」の型）。経理担当者は請求書の画面を閉じ、
 * 荷主一覧を開き、名前で探し直すことになる。
 *
 * <p><strong>連絡先は写し取らない。</strong> 宛名は発行時点の事実として凍結するが
 * （C7）、<strong>連絡先は「いま届く先」である</strong>。写すと、
 * 荷主が電話番号を変えた日から古い番号にかけ続けることになる。
 */
public interface ShipperContactPort {

    /**
     * 荷主の連絡先。
     *
     * <p><strong>見つからない荷主を例外にしない。</strong> 空を返すと画面は
     * 「連絡先が登録されていません」と出す。ここで止めると請求書の画面ごと開けない。
     */
    Optional<Contact> findContact(String shipperId);

    /**
     * 連絡先（<strong>素の値だけを運ぶ</strong>。ADR-005）。
     *
     * @param shipperName いまの荷主名（<strong>請求書の凍結した宛名とは別である</strong>）
     */
    record Contact(String shipperName, String email, String phone) {
    }
}
