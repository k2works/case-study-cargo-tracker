package com.example.cargotracker.handling.application.internal.queryservices;

import com.example.cargotracker.handling.domain.model.CustomsStatus;
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
 * @param status            通関状態。<strong>述語は状態自身に委ねる</strong>
 *                          （画面で「CLEARED なら」と書くと規則が 2 か所に散る）
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
        CustomsStatus status,
        String statusLabel,
        String statusBadge,
        Instant declaredAt,
        Instant clearedAt,
        Instant heldSince,
        boolean heldTooLong,
        String shipperName) {

    /**
     * 引取に進めるか。
     *
     * <p><strong>状態自身の述語に委ねる。</strong> ここで文字列比較を書くと、
     * {@code CustomsStatus.allowsClaim()} とは別の述語になり、状態が増えたときに
     * 片方だけが更新される（まさにその形になっていた。IT11 レビュー）。
     */
    public boolean allowsClaim() {
        return status.allowsClaim();
    }

    /** 対応が要る状態か（一覧の警告・ダッシュボードの件数と同じ判断）。 */
    public boolean needsAttention() {
        return status.needsAttention();
    }

    /** 絞り込みの一致に使う列挙子名。 */
    public String statusName() {
        return status.name();
    }
}
