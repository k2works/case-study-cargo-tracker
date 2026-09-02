package com.example.trackingms.application.internal.queryservices;

import java.util.List;

/** 荷主向け追跡一覧。紐付けなしと貨物なしを区別する。 */
public record ShipperTrackingQueryResult(boolean linked, String contactMessage,
        List<ShipperTrackingSummary> cargos) {

    private static final String CONTACT_MESSAGE =
            "荷主との紐付けがありません。ご依頼元の営業担当へお問い合わせください。";

    public static ShipperTrackingQueryResult unlinked() {
        return new ShipperTrackingQueryResult(false, CONTACT_MESSAGE, List.of());
    }

    public static ShipperTrackingQueryResult linked(List<ShipperTrackingSummary> cargos) {
        return new ShipperTrackingQueryResult(true, null, cargos);
    }
}
