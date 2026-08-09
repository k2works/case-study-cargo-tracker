package com.example.cargotracker.tracking.application.internal.queryservices;

import java.time.Instant;

/**
 * 例外イベント一覧・詳細の 1 行（US19 / US20）。
 *
 * <p><strong>「誰に連絡するのか」を持つ。</strong> 例外の一覧は追跡管理者にとって
 * 「連絡すべき仕事の待ち行列」である。荷主の名前が読めないと、1 件ずつ予約を
 * 開いて確かめることになる（IT9 のふりかえり T2）。
 *
 * @param id                 例外 ID
 * @param trackingNumber     追跡番号
 * @param bookingId          予約 ID。**荷主へ連絡するための入口**
 * @param exceptionTypeLabel 例外種別の表示名
 * @param locationUnlocode   発生場所
 * @param occurredAt         発生日時
 * @param description        状況
 * @param escalationFlag     エスカレーション中か（US20）
 * @param statusBeforeLabel  発生前の輸送状態の表示名。**解決すればここへ戻る**
 * @param resolvedAt         対応日時。未解決なら {@code null}
 * @param resolutionNotes    対応内容
 * @param shipperName        荷主名。**連絡先を探す手がかり**
 */
public record TrackingExceptionView(
        long id,
        String trackingNumber,
        String bookingId,
        String exceptionTypeLabel,
        String locationUnlocode,
        Instant occurredAt,
        String description,
        boolean escalationFlag,
        String statusBeforeLabel,
        Instant resolvedAt,
        String resolutionNotes,
        String shipperName) {

    /** 未解決か。**画面の出し分けは同じ述語を使う。** */
    public boolean isUnresolved() {
        return resolvedAt == null;
    }

    /** エスカレーション中（紛失で未解決）か。**管理者が見るのはこれである。** */
    public boolean isEscalating() {
        return escalationFlag && isUnresolved();
    }
}
