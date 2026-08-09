package com.example.cargotracker.handling.application.internal.queryservices;

import java.time.Instant;

/**
 * 通関申告一覧・詳細の 1 行（US29）。
 *
 * <p><strong>画面が判断を持たないようにする。</strong> 表示名・バッジ・警告の要否は
 * ここまでで決まっている。
 *
 * @param id                申告 ID
 * @param declarationNumber 申告番号
 * @param trackingNumber    追跡番号。**貨物へ戻る入口**
 * @param bookingId         予約 ID
 * @param statusName        通関状態の列挙子名（絞り込みの一致に使う）
 * @param statusLabel       通関状態の表示名
 * @param statusBadge       状態のバッジ（Bootstrap のクラス）
 * @param declaredAt        申告日時
 * @param clearedAt         通関完了日時。未完了なら {@code null}
 * @param heldSince         いまの留置が始まった日時。留置でなければ {@code null}
 * @param heldTooLong       **留置が長引いているか**（放置するとコストが発生する）
 * @param shipperName       荷主名。**連絡先を探す手がかり**
 */
public record CustomsDeclarationView(
        long id,
        String declarationNumber,
        String trackingNumber,
        String bookingId,
        String statusName,
        String statusLabel,
        String statusBadge,
        Instant declaredAt,
        Instant clearedAt,
        Instant heldSince,
        boolean heldTooLong,
        String shipperName) {

    /** 引取に進めるか。**画面の出し分けは同じ述語を使う。** */
    public boolean allowsClaim() {
        return "CLEARED".equals(statusName);
    }

    /** まだ通関が終わっていないか。 */
    public boolean isPending() {
        return "PENDING".equals(statusName);
    }
}
