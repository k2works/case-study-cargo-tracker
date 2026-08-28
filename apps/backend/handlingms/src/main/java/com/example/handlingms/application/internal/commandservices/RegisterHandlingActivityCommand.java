package com.example.handlingms.application.internal.commandservices;

import java.time.Instant;

/**
 * 荷役作業を記録する要求（US15・US16）。
 *
 * <p>入口の形（HTTP の本文）を持ち込まない。画面が項目を足しても、ここが変わらなければ
 * ユースケースは影響を受けない。
 *
 * @param trackingNumber 追跡番号。荷役作業員が手元の貨物から読む（US15-1）
 * @param type 荷役の種別
 * @param locationUnLocode 作業場所。地点マスタから選ぶ（US15-3）
 * @param completionTime 作業日時
 * @param operatorName 作業者
 * @param voyageNumber 航海番号。積込・荷降しでは必須
 * @param consigneeConfirmation 荷受人の確認。引取では必須（[ADR-023] 決定 4）
 */
public record RegisterHandlingActivityCommand(String trackingNumber, String type,
        String locationUnLocode, Instant completionTime, String operatorName,
        String voyageNumber, String consigneeConfirmation) {
}
