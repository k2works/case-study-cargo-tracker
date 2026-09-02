package com.example.trackingms.application.internal.queryservices;

import java.util.List;

/** 荷主向け追跡詳細。自社貨物であることを確認したあとにだけ作る。 */
public record ShipperTrackingDetail(String trackingNumber, String status, String statusLabel,
        String locationName, java.time.LocalDate estimatedArrival, boolean hasException,
        boolean urgent, List<ShipperTrackingEvent> events,
        List<ShipperTrackingNotice> notices) {

    /**
     * 過去の知らせを伴う詳細（IT16 レビュー 高 3）。
     *
     * <p><strong>ポップアップは出した時点で既読になる</strong>（[ADR-032] 決定 4）。
     * 読み直せる場所が無いと、回線が切れた・タブを閉じた・見落とした荷主は
     * その知らせに<strong>二度と到達できない</strong>。
     */
    static ShipperTrackingDetail from(ShipperTrackingSummary summary,
            List<ShipperTrackingEvent> events, List<ShipperTrackingNotice> notices) {
        return new ShipperTrackingDetail(summary.trackingNumber(), summary.status(),
                summary.statusLabel(), summary.locationName(), summary.estimatedArrival(),
                summary.hasException(), summary.urgent(), events,
                notices == null ? List.of() : List.copyOf(notices));
    }
}
