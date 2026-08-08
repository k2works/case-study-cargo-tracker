package com.example.cargotracker.handling.application.internal.queryservices;

import java.time.Instant;

/**
 * 荷役作業の表示用データ。
 *
 * <p><strong>画面にドメインモデルを渡さない。</strong> 表示のために集約へ getter を
 * 増やし続けると、集約が画面の都合を抱え込む。
 *
 * @param completionTime 作業日時
 * @param typeLabel      荷役種別の日本語ラベル（正典は {@code HandlingType}）
 * @param locationUnlocode 作業場所（UN/LOCODE）
 * @param voyageNumber   航海番号。無ければ空文字
 * @param trackingNumber 読み取った追跡番号。IT6 以前の記録では空文字
 * @param bookingId      予約 ID
 * @param consigneeName  引取で実際に受け取った方の氏名。引取以外は空文字（US16）
 * @param note           担当者メモ。無ければ空文字
 * @param operatorName   作業員名。無ければ空文字
 */
public record HandlingActivityView(
        Instant completionTime,
        String typeLabel,
        String locationUnlocode,
        String voyageNumber,
        String trackingNumber,
        String bookingId,
        String consigneeName,
        String note,
        String operatorName) {

    /**
     * 引き渡しの記録があるか（US16 / レビュー H3）。
     *
     * <p><strong>「受け取っていない」というクレームは数日〜数週間後に来る。</strong>
     * そのとき画面に受取人が出ていないと、誰に渡したかを示せない。
     */
    public boolean hasClaimRecord() {
        return consigneeName != null && !consigneeName.isBlank();
    }
}
