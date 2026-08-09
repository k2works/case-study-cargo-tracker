package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;

/**
 * 誤配を検知した荷役の写し（US28）。
 *
 * <p><strong>Handling のテーブルを読みに行かないために持つ。</strong> 予約詳細は
 * 「どこで・いつ」誤配が検知されたかを示す。IT11 はこれを
 * {@code handling_activity} を JOIN して読んでいたが、
 * <strong>BC をまたぐ SQL は ArchUnit にも JIG にも映らない</strong>
 * （IT11 レビュー C28）。
 *
 * <p>荷役の登録は既に {@code HandlingActivityRegisteredEvent} で場所と日時を運んでいる。
 * <strong>運ばれてきた事実を写すのが結果整合の形である</strong>（ADR-009）。
 *
 * <p>場所と日時は<strong>ひと組で動く</strong>。別々に持つと「場所は分かるが
 * いつのことか分からない」状態を作れてしまう。
 *
 * @param location   検知した荷役の場所（＝貨物の現在地）
 * @param detectedAt 検知した荷役の作業日時
 */
public record MisrouteDetection(Location location, Instant detectedAt) {

    public MisrouteDetection {
        if (location == null) {
            throw new IllegalArgumentException("検知した場所は必須です");
        }
        if (detectedAt == null) {
            throw new IllegalArgumentException("検知した日時は必須です");
        }
    }

    /**
     * 永続化された値から復元する。
     *
     * <p><strong>片方でも欠けていれば「写しが無い」とみなす。</strong> 列が無かった
     * ころに誤配になった貨物は値を持たない。復元で拒むと、
     * <strong>その予約の画面ごと 500 になる</strong>（V22 で同じ形の欠陥を作った）。
     *
     * @return 復元できなければ {@code null}
     */
    public static MisrouteDetection reconstruct(Location location, Instant detectedAt) {
        if (location == null || detectedAt == null) {
            return null;
        }
        return new MisrouteDetection(location, detectedAt);
    }
}
