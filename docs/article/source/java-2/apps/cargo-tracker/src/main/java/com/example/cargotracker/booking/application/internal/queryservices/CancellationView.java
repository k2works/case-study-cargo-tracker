package com.example.cargotracker.booking.application.internal.queryservices;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 画面に出すキャンセル申請（US30）。
 *
 * <p><strong>意味のまとまりごとに入れ子へ分けている</strong>（IT17 の R6）。
 * 以前は 19 個の要素が一列に並び、{@code requestedBy} と {@code decidedBy}、
 * {@code origin} と {@code destination} を<strong>取り違えてもコンパイルが通った</strong>。
 *
 * @param id       申請 ID
 * @param cargo    対象の貨物
 * @param request  申請そのもの
 * @param progress 承認の進み具合
 * @param discharge 陸揚げ地
 */
public record CancellationView(
        long id,
        CargoSummary cargo,
        Submission request,
        Progress progress,
        Discharge discharge) {

    /**
     * 対象の貨物（読み取り側の写し。行が読めなければ各値は {@code null}）。
     *
     * @param bookingId      予約 ID
     * @param trackingNumber 追跡番号
     * @param shipperName    荷主名
     * @param origin         出発地 UN/LOCODE
     * @param destination    目的地 UN/LOCODE
     */
    public record CargoSummary(
            String bookingId,
            String trackingNumber,
            String shipperName,
            String origin,
            String destination) { }

    /**
     * 申請そのもの。
     *
     * @param reason      申請の理由
     * @param by          申請者
     * @param at          申請日時
     * @param feePercent  キャンセル料の料率（％）
     */
    public record Submission(String reason, String by, Instant at, BigDecimal feePercent) { }

    /**
     * 承認の進み具合。
     *
     * @param statusLabel 状態の表示名
     * @param statusBadge 状態のバッジ用クラス
     * @param pending     まだ決まっていないか
     * @param decidedBy   決めた人。<strong>未決では {@code null}</strong>
     * @param decidedAt   決めた日時。同上
     * @param reason      却下の理由。<strong>承認・未決では {@code null}</strong>
     */
    public record Progress(
            String statusLabel,
            String statusBadge,
            boolean pending,
            String decidedBy,
            Instant decidedAt,
            String reason) {

        /** 却下の理由があるか。 */
        public boolean hasReason() {
            return reason != null;
        }
    }

    /**
     * 陸揚げ地（どこで降ろすか）。
     *
     * <p><strong>候補は戻り値をそのまま出す</strong>（ADR-021 の T1）。
     * 画面で組み立て直すと、集約が守っている「候補の中からしか選べない」と
     * 見えている選択肢がずれる。
     *
     * @param currentUnlocode いまの場所。<strong>読めなければ {@code null}</strong>
     * @param candidates      候補（現在地の港とまだ着いていない寄港地）
     * @param unlocode        決まった陸揚げ地。<strong>承認前・却下では {@code null}</strong>
     */
    public record Discharge(String currentUnlocode, List<String> candidates, String unlocode) {

        /** <strong>候補を写して持つ。</strong> 外から差し替えられると、
         * 画面に出す選択肢と承認が受け付ける値がずれる。 */
        public Discharge {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        /** いまの場所が読めたか。 */
        public boolean hasCurrentLocation() {
            return currentUnlocode != null;
        }
    }

    // --- 画面が呼ぶ名前（委譲するアクセサ）---

    /** @return 予約 ID */
    public String bookingId() {
        return cargo.bookingId();
    }

    /** @return 追跡番号 */
    public String trackingNumber() {
        return cargo.trackingNumber();
    }

    /** @return 荷主名 */
    public String shipperName() {
        return cargo.shipperName();
    }

    /** @return 出発地 UN/LOCODE */
    public String origin() {
        return cargo.origin();
    }

    /** @return 目的地 UN/LOCODE */
    public String destination() {
        return cargo.destination();
    }

    /** @return 申請の理由 */
    public String reason() {
        return request.reason();
    }

    /** @return 申請者 */
    public String requestedBy() {
        return request.by();
    }

    /** @return 申請日時 */
    public Instant requestedAt() {
        return request.at();
    }

    /** @return キャンセル料の料率（％） */
    public BigDecimal feePercent() {
        return request.feePercent();
    }

    /** @return 状態の表示名 */
    public String statusLabel() {
        return progress.statusLabel();
    }

    /** @return 状態のバッジ用クラス */
    public String statusBadge() {
        return progress.statusBadge();
    }

    /** @return まだ決まっていないか */
    public boolean pending() {
        return progress.pending();
    }

    /** @return 決めた人 */
    public String decidedBy() {
        return progress.decidedBy();
    }

    /** @return 決めた日時 */
    public Instant decidedAt() {
        return progress.decidedAt();
    }

    /** @return 却下の理由 */
    public String decisionReason() {
        return progress.reason();
    }

    /** @return いまの場所 */
    public String currentUnlocode() {
        return discharge.currentUnlocode();
    }

    /** @return 陸揚げ地の候補 */
    public List<String> candidates() {
        return discharge.candidates();
    }

    /** @return 決まった陸揚げ地 */
    public String dischargeUnlocode() {
        return discharge.unlocode();
    }

    /** いまの場所が読めたか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
    public boolean hasCurrentLocation() {
        return discharge.hasCurrentLocation();
    }

    /** 却下の理由があるか。 */
    public boolean hasDecisionReason() {
        return progress.hasReason();
    }
}
