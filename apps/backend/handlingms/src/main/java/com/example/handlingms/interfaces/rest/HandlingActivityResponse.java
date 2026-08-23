package com.example.handlingms.interfaces.rest;

import com.example.handlingms.domain.model.ConsigneeConfirmation;
import com.example.handlingms.domain.model.HandlingActivity;
import com.example.handlingms.domain.model.HandlingVoyageNumber;
import java.time.Instant;

/**
 * 荷役作業の記録（US15・US16）。
 *
 * <p>作業場所は<strong>名前まで返す</strong>。UN/LOCODE だけを返すと、画面が 5 文字の
 * コードから地点名を引き直すことになり、その対応表がフロントとサーバの 2 箇所に増える。
 *
 * @param id 記録の識別子
 * @param bookingId 予約番号
 * @param type 荷役の種別
 * @param locationUnLocode 作業場所
 * @param locationName 作業場所の名前
 * @param completionTime 作業日時
 * @param operatorName 作業者
 * @param voyageNumber 航海番号。受領・引取では {@code null}
 * @param consigneeConfirmation 荷受人の確認。引取以外では {@code null}
 * @param offRoute 予定と違う場所での作業だったか（[ADR-023] 決定 3）
 */
public record HandlingActivityResponse(Long id, String bookingId, String type,
        String locationUnLocode, String locationName, Instant completionTime,
        String operatorName, String voyageNumber, String consigneeConfirmation,
        boolean offRoute) {

    public static HandlingActivityResponse from(HandlingActivity activity) {
        return new HandlingActivityResponse(
                activity.id(),
                activity.bookingId().value(),
                activity.type().name(),
                activity.location().unLocode(),
                activity.location().name(),
                activity.completionTime(),
                activity.operatorName(),
                activity.voyageNumber().map(HandlingVoyageNumber::value).orElse(null),
                activity.consigneeConfirmation().map(ConsigneeConfirmation::confirmedBy)
                        .orElse(null),
                activity.offRoute());
    }
}
