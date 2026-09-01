package com.example.trackingms.application.internal.queryservices;

import com.example.trackingms.domain.model.valueobjects.TrackingNotice;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 荷主が読み直す知らせ 1 件（IT16 レビュー 高 3）。
 *
 * <p><strong>いつの話かを添える。</strong>「問題が発生しました」だけでは、
 * 荷主が最初に聞くのは「それはいつですか」である。
 *
 * @param noticedAt 通知の時刻。<strong>業務タイムゾーンで返す</strong>
 * @param message 荷主に見せる文言
 */
public record ShipperTrackingNotice(String noticedAt, String message) {

    static ShipperTrackingNotice from(TrackingNotice notice, ZoneId zone) {
        return new ShipperTrackingNotice(
                ZonedDateTime.ofInstant(notice.noticedAt(), zone).toString(), notice.message());
    }
}
