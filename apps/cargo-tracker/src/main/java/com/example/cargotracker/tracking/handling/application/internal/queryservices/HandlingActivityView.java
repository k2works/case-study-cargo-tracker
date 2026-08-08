package com.example.cargotracker.tracking.handling.application.internal.queryservices;

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
 * @param bookingId      予約 ID
 * @param operatorName   作業員名。無ければ空文字
 */
public record HandlingActivityView(
        Instant completionTime,
        String typeLabel,
        String locationUnlocode,
        String voyageNumber,
        String bookingId,
        String operatorName) {
}
