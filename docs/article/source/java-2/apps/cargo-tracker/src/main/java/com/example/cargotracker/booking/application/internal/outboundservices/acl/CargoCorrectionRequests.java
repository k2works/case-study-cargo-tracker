package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import java.time.Instant;
import java.util.List;

/**
 * 引取記録の訂正・取り消し申請を読む出力ポート（Booking → Handling の ACL。IT12 の C8）。
 *
 * <p><strong>承認待ちの間、貨物は「配送完了」のままである。</strong> 取り消しが
 * 申請されていても予約詳細には何も出ず、荷主から「まだ届いていない」と電話を
 * 受けた営業担当者は配送完了としか答えられない。
 * <strong>状態が動かないことは正しい</strong>（承認なしに戻してはならない）が、
 * <strong>申請が出ていることまで見えないのは別の問題である</strong>。
 *
 * <p><strong>SQL で JOIN しない。</strong> 訂正の申請は Handling の持ち物であり、
 * 越境してよいのは ACL ポートだけである（ADR-012。SQL の越境は
 * {@code MapperTableOwnershipTest} が検出する。ADR-015）。
 *
 * <p>運ぶのは<strong>表示のための素の値だけ</strong>である（ADR-005）。
 * {@code CorrectionRequest} を渡すと Booking が Handling のドメインを
 * 参照することになる（ArchUnit ルール 4）。
 */
public interface CargoCorrectionRequests {

    /**
     * 予約に紐づく訂正・取り消し申請を引く（<strong>読み取り専用</strong>）。
     *
     * <p>並び順は<strong>承認待ちを先に、申請の新しい順</strong>。
     * 営業担当者が知りたいのは「いま止まっている話があるか」である。
     *
     * @param bookingId 予約 ID
     * @return 申請が無ければ空のリスト。<strong>荷役の記録が無い予約でも空</strong>
     */
    List<CorrectionSummary> findByBookingId(String bookingId);

    /**
     * 複数の予約について、<strong>決着していない申請を持つものだけ</strong>を返す（IT13 レビュー C4）。
     *
     * <p><strong>1 件ずつ聞かない。</strong> 一覧を描くたびに行数分の問い合わせが飛ぶと、
     * 件数に比例して重くなる。
     *
     * @param bookingIds 予約 ID の集合。<strong>空なら空を返す</strong>
     * @return 承認待ちの申請を持つ予約 ID
     */
    java.util.Set<String> findBookingIdsWithPendingCorrection(
            java.util.Collection<String> bookingIds);

    /**
     * 申請 1 件（表示用）。
     *
     * @param typeLabel      種別（訂正・取り消し）の表示名
     * @param submission 申請そのもの
     * @param progress   承認の進み具合
     */
    record CorrectionSummary(
            String typeLabel,
            Submission submission,
            Progress progress) {

        /**
         * 申請そのもの。
         *
         * @param by     申請者
         * @param at     申請日時
         */
        public record Submission(String reason, String by, Instant at) { }

        /**
         * 承認の進み具合。
         *
         */
        public record Progress(
                String statusLabel, String statusBadge, boolean pending, String decisionReason) { }

        // --- 呼び出し側が使う名前（委譲するアクセサ）---

        /** @return 申請の理由 */
        public String reason() {
            return submission.reason();
        }

        /** @return 申請者 */
        public String requestedBy() {
            return submission.by();
        }

        /** @return 申請日時 */
        public Instant requestedAt() {
            return submission.at();
        }

        /** @return 状態の表示名 */
        public String statusLabel() {
            return progress.statusLabel();
        }

        /** @return 状態のバッジ */
        public String statusBadge() {
            return progress.statusBadge();
        }

        /** @return まだ決まっていないか */
        public boolean pending() {
            return progress.pending();
        }

        /** @return 却下の理由 */
        public String decisionReason() {
            return progress.decisionReason();
        }


        /** 却下の理由があるか。**画面の出し分けは本述語をそのまま呼ぶ。** */
        public boolean hasDecisionReason() {
            return progress.decisionReason() != null
                    && !progress.decisionReason().isBlank();
        }
    }
}
