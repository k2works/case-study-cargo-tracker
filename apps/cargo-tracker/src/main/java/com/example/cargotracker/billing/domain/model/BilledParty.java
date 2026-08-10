package com.example.cargotracker.billing.domain.model;

/**
 * 請求書が指す相手の写し（US23 / IT13 レビュー C7）。
 *
 * <p><strong>発行の時点で凍結する。</strong> 荷主が改名しても、
 * 発行済みの請求書の宛名は変わってはならない。
 * 金額を丸め後のスナップショットで持つのと同じ理由である
 * （「誰にいくら請求したか」は請求書自身が持つ）。
 *
 * <p><strong>N+1 の解決でもある。</strong> 表示に要る値を請求書が持てば、
 * 一覧を描くのに 1 行ずつ ACL ポートを呼ぶ必要が無くなる。
 *
 * @param shipperName    荷主名（宛名）
 * @param trackingNumber 追跡番号。<strong>経理担当者が貨物を指す値である</strong>
 */
public record BilledParty(String shipperName, String trackingNumber) {

    public BilledParty {
        shipperName = shipperName == null ? "" : shipperName.strip();
        trackingNumber = trackingNumber == null ? "" : trackingNumber.strip();
    }

    /** 名前を持たない写し（**古い行を読み戻すため**）。 */
    public static BilledParty unknown() {
        return new BilledParty("", "");
    }
}
