package com.example.cargotracker.tracking.application.internal.queryservices;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 例外イベント一覧・詳細の 1 行（US19 / US20）。
 *
 * <p><strong>「誰に連絡するのか」を持つ。</strong> 例外の一覧は追跡管理者にとって
 * 「連絡すべき仕事の待ち行列」である。荷主の名前が読めないと、1 件ずつ予約を
 * 開いて確かめることになる（IT9 のふりかえり T2）。
 *
 * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
 * 以前は 13 個の要素が一列に並び、{@code occurredAt} と {@code resolvedAt}、
 * {@code description} と {@code resolutionNotes} という
 * <strong>「発生」と「対応」の対が隣り合って</strong>いた — 取り違えると
 * 未解決の例外が解決済みに見える。
 *
 * <p>画面が呼ぶ名前は委譲するアクセサで残している。
 *
 * @param id         例外 ID
 * @param cargo      対象の貨物（<strong>誰に連絡するのか</strong>）
 * @param occurrence 何が起きたか
 * @param resolution どう対応したか
 */
public record TrackingExceptionView(
        long id,
        CargoSummary cargo,
        Occurrence occurrence,
        Resolution resolution) {

    /**
     * 対象の貨物。
     *
     * @param trackingNumber 追跡番号
     * @param bookingId      予約 ID。<strong>荷主へ連絡するための入口</strong>
     * @param shipperName    荷主名。<strong>連絡先を探す手がかり</strong>
     */
    public record CargoSummary(String trackingNumber, String bookingId, String shipperName) { }

    /**
     * 何が起きたか。
     *
     * @param typeLabel        例外種別の表示名
     * @param locationUnlocode 発生場所
     * @param at               発生日時
     * @param description      状況
     * @param escalating       エスカレーション中か（US20）
     * @param statusBeforeLabel 発生前の輸送状態の表示名。<strong>解決すればここへ戻る</strong>
     */
    public record Occurrence(
            String typeLabel,
            String locationUnlocode,
            Instant at,
            String description,
            boolean escalating,
            String statusBeforeLabel) { }

    /**
     * どう対応したか。
     *
     * @param at             対応日時。未解決なら {@code null}
     * @param notes          対応内容
     * @param revisedArrival 対応で決まった新しい到着予定日（US19）。無ければ {@code null}
     */
    public record Resolution(Instant at, String notes, LocalDate revisedArrival) { }

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

    /** @return 例外種別の表示名 */
    public String exceptionTypeLabel() {
        return occurrence.typeLabel();
    }

    /** @return 発生場所 */
    public String locationUnlocode() {
        return occurrence.locationUnlocode();
    }

    /** @return 発生日時 */
    public Instant occurredAt() {
        return occurrence.at();
    }

    /** @return 状況 */
    public String description() {
        return occurrence.description();
    }

    /** @return エスカレーション中か（US20） */
    public boolean escalationFlag() {
        return occurrence.escalating();
    }

    /** @return 発生前の輸送状態の表示名 */
    public String statusBeforeLabel() {
        return occurrence.statusBeforeLabel();
    }

    /** @return 対応日時。未解決なら {@code null} */
    public Instant resolvedAt() {
        return resolution.at();
    }

    /** @return 対応内容 */
    public String resolutionNotes() {
        return resolution.notes();
    }

    /** @return 新しい到着予定日（US19） */
    public LocalDate revisedArrival() {
        return resolution.revisedArrival();
    }

    /** 未解決か。**画面の出し分けは同じ述語を使う。** */
    public boolean isUnresolved() {
        return resolution.at() == null;
    }

    /** エスカレーション中（紛失で未解決）か。**管理者が見るのはこれである。** */
    public boolean isEscalating() {
        return occurrence.escalating() && isUnresolved();
    }
}
