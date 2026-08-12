package com.example.cargotracker.handling.application.internal.queryservices;

import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import java.time.Instant;

/**
 * 通関申告一覧・詳細の 1 行（US29）。
 *
 * <p><strong>画面が判断を持たないようにする。</strong> 表示名・バッジ・警告の要否は
 * ここまでで決まっている。
 *
 * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
 * 以前は 12 個の要素が一列に並び、{@code declaredAt} / {@code clearedAt} /
 * {@code heldSince} という<strong>3 つの {@code Instant} が隣り合って</strong>いた。
 *
 * <p>画面が呼ぶ名前は委譲するアクセサで残している。
 *
 * @param id                申告 ID
 * @param declarationNumber 申告番号
 * @param cargo             対象の貨物（<strong>誰に連絡し、どこへ戻るか</strong>）
 * @param progress          通関の進み具合
 */
public record CustomsDeclarationView(
        long id,
        String declarationNumber,
        CargoSummary cargo,
        Progress progress) {

    /**
     * 対象の貨物。
     *
     * @param trackingNumber 追跡番号。<strong>貨物へ戻る入口</strong>
     * @param bookingId      予約 ID
     * @param shipperName    荷主名。<strong>連絡先を探す手がかり</strong>
     */
    public record CargoSummary(String trackingNumber, String bookingId, String shipperName) { }

    /**
     * 通関の進み具合。
     *
     * @param status      通関状態。<strong>述語は状態自身に委ねる</strong>
     *                    （画面で「CLEARED なら」と書くと規則が 2 か所に散る）
     * @param label       通関状態の表示名
     * @param badge       状態のバッジ（Bootstrap のクラス）
     * @param declaredAt  申告日時
     * @param clearedAt   通関完了日時。未完了なら {@code null}
     * @param heldSince   いまの留置が始まった日時。留置でなければ {@code null}
     * @param heldTooLong <strong>留置が長引いているか</strong>（放置するとコストが発生する）
     */
    public record Progress(
            CustomsStatus status,
            String label,
            String badge,
            Instant declaredAt,
            Instant clearedAt,
            Instant heldSince,
            boolean heldTooLong) { }

    // --- 画面が呼ぶ名前（委譲するアクセサ）---

    /** @return 追跡番号 */
    public String trackingNumber() {
        return cargo.trackingNumber();
    }

    /** @return 予約 ID */
    public String bookingId() {
        return cargo.bookingId();
    }

    /** @return 荷主名 */
    public String shipperName() {
        return cargo.shipperName();
    }

    /** @return 通関状態 */
    public CustomsStatus status() {
        return progress.status();
    }

    /** @return 通関状態の表示名 */
    public String statusLabel() {
        return progress.label();
    }

    /** @return 状態のバッジ */
    public String statusBadge() {
        return progress.badge();
    }

    /** @return 申告日時 */
    public Instant declaredAt() {
        return progress.declaredAt();
    }

    /** @return 通関完了日時 */
    public Instant clearedAt() {
        return progress.clearedAt();
    }

    /** @return いまの留置が始まった日時 */
    public Instant heldSince() {
        return progress.heldSince();
    }

    /** @return 留置が長引いているか */
    public boolean heldTooLong() {
        return progress.heldTooLong();
    }


    /**
     * 引取に進めるか。
     *
     * <p><strong>状態自身の述語に委ねる。</strong> ここで文字列比較を書くと、
     * {@code CustomsStatus.allowsClaim()} とは別の述語になり、状態が増えたときに
     * 片方だけが更新される（まさにその形になっていた。IT11 レビュー）。
     */
    public boolean allowsClaim() {
        return progress.status().allowsClaim();
    }

    /** 対応が要る状態か（一覧の警告・ダッシュボードの件数と同じ判断）。 */
    public boolean needsAttention() {
        return progress.status().needsAttention();
    }

    /** 絞り込みの一致に使う列挙子名。 */
    public String statusName() {
        return progress.status().name();
    }
}
