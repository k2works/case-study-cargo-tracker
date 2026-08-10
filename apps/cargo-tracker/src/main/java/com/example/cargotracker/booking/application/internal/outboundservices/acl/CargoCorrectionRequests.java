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
     * 申請 1 件（表示用）。
     *
     * @param typeLabel      種別（訂正・取り消し）の表示名
     * @param reason         申請の理由。<strong>荷主に説明するときの材料である</strong>
     * @param requestedBy    申請者
     * @param requestedAt    申請日時
     * @param statusLabel    状態の表示名
     * @param statusBadge    状態のバッジ（正典は {@code CorrectionStatus}）
     * @param pending        まだ決まっていないか。<strong>「確認中です」と答えられるのは
     *                       この 1 件だけである</strong>
     * @param decisionReason 却下の理由。<strong>却下されたのに理由が読めないと、
     *                       営業担当者は荷主に誤った見通しを伝える</strong>。
     *                       承認・未決なら {@code null}
     */
    record CorrectionSummary(
            String typeLabel, String reason, String requestedBy, Instant requestedAt,
            String statusLabel, String statusBadge, boolean pending, String decisionReason) {

        /** 却下の理由があるか。**画面の出し分けは本述語をそのまま呼ぶ。** */
        public boolean hasDecisionReason() {
            return decisionReason != null && !decisionReason.isBlank();
        }
    }
}
