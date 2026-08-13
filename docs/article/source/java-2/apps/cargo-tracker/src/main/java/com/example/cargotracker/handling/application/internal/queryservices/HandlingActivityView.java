package com.example.cargotracker.handling.application.internal.queryservices;

import java.time.Instant;

/**
 * 荷役作業の表示用データ。
 *
 * <p><strong>画面にドメインモデルを渡さない。</strong> 表示のために集約へ getter を
 * 増やし続けると、集約が画面の都合を抱え込む。
 *
 * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
 * 以前は 13 個の要素が一列に並び、{@code trackingNumber} / {@code bookingId} /
 * {@code consigneeName} / {@code note} / {@code operatorName} が
 * <strong>すべて {@code String} で隣り合っていた</strong>。
 *
 * <p>画面が呼ぶ名前は委譲するアクセサで残している。
 *
 * @param id       荷役作業 ID（US36。訂正・取り消しの対象を指す）
 * @param work     作業そのもの（いつ・何を・どこで・誰が）
 * @param cargo    対象の貨物
 * @param claim    引取の記録か（US36。<strong>取り消しで戻る状態を持つのは引取だけである</strong>）
 * @param cancelled 取り消し済みか（US36）。<strong>行は消さない</strong>
 */
public record HandlingActivityView(
        long id,
        Work work,
        CargoSummary cargo,
        boolean claim,
        boolean cancelled) {

    /**
     * 作業そのもの。
     *
     * @param completionTime   作業日時
     * @param typeLabel        荷役種別の日本語ラベル（正典は {@code HandlingType}）
     * @param locationUnlocode 作業場所（UN/LOCODE）
     * @param voyageNumber     航海番号。無ければ空文字
     * @param operatorName     作業員名。無ければ空文字
     * @param note             担当者メモ。無ければ空文字
     */
    public record Work(
            Instant completionTime,
            String typeLabel,
            String locationUnlocode,
            String voyageNumber,
            String operatorName,
            String note) { }

    /**
     * 対象の貨物。
     *
     * @param trackingNumber 読み取った追跡番号。IT6 以前の記録では空文字
     * @param bookingId      予約 ID
     * @param consigneeName  引取で実際に受け取った方の氏名。引取以外は空文字（US16）
     * @param typeLabel      貨物種別の表示名（US05）。<strong>現物に触る人が特別な
     *                       取り扱いに気づけるようにする。</strong> 一般貨物・不明なら空文字
     */
    public record CargoSummary(
            String trackingNumber,
            String bookingId,
            String consigneeName,
            String typeLabel) { }

    // --- 画面が呼ぶ名前（委譲するアクセサ）---

    /** @return 作業日時 */
    public Instant completionTime() {
        return work.completionTime();
    }

    /** @return 荷役種別の日本語ラベル */
    public String typeLabel() {
        return work.typeLabel();
    }

    /** @return 作業場所（UN/LOCODE） */
    public String locationUnlocode() {
        return work.locationUnlocode();
    }

    /** @return 航海番号 */
    public String voyageNumber() {
        return work.voyageNumber();
    }

    /** @return 作業員名 */
    public String operatorName() {
        return work.operatorName();
    }

    /** @return 担当者メモ */
    public String note() {
        return work.note();
    }

    /** @return 追跡番号 */
    public String trackingNumber() {
        return cargo.trackingNumber();
    }

    /** @return 予約 ID */
    public String bookingId() {
        return cargo.bookingId();
    }

    /** @return 引取で受け取った方の氏名 */
    public String consigneeName() {
        return cargo.consigneeName();
    }

    /** @return 貨物種別の表示名 */
    public String cargoTypeLabel() {
        return cargo.typeLabel();
    }

    /**
     * 特別な取り扱いが要る貨物か（US05）。
     *
     * <p><strong>危険物・冷凍だと現場が気づけない状態にしない。</strong>
     * 申告を登録しても、船に積む人に伝わらなければ安全な輸送にならない。
     */
    public boolean needsSpecialHandling() {
        return cargo.typeLabel() != null && !cargo.typeLabel().isBlank();
    }

    /**
     * 引き渡しの記録があるか（US16 / レビュー H3）。
     *
     * <p><strong>「受け取っていない」というクレームは数日〜数週間後に来る。</strong>
     * そのとき画面に受取人が出ていないと、誰に渡したかを示せない。
     */
    public boolean hasClaimRecord() {
        return cargo.consigneeName() != null && !cargo.consigneeName().isBlank();
    }

    /**
     * 訂正・取り消しを申請できるか（US36）。
     *
     * <p><strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> 呼び出し側で
     * 「引取なら」と書くと、規則が 2 か所に散る。
     *
     * <p>取り消し済みには申請できない。<strong>精算済みかどうかは
     * ここでは分からない</strong> — 申請の時点でアプリケーション層が拒む。
     */
    public boolean correctable() {
        return claim && !cancelled;
    }
}
