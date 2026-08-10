package com.example.cargotracker.handling.domain.repository;

import com.example.cargotracker.handling.domain.model.CorrectionRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 訂正・取り消し申請の出力ポート（US36）。実装はインフラ層に置く（DIP）。
 *
 * <p><strong>元の荷役の記録は消さない。</strong> 申請は別の表に積み、
 * 荷役の行には「取り消された事実」だけを書く。
 */
public interface CorrectionRequestRepository {

    /** 新しい申請を保存し、採番された ID を返す。 */
    long save(CorrectionRequest request);

    /**
     * 決定を反映する（楽観的ロック付き）。
     *
     * @return 更新できたか。<strong>0 件は「別の担当者が先に決めた」ことを表す</strong>
     */
    boolean update(CorrectionRequest request);

    Optional<CorrectionRequest> findById(long id);

    /**
     * 承認待ちの申請（追跡管理者の待ち行列）。
     *
     * <p><strong>古い順に返す。</strong> 待たせている申請から片づける。
     */
    List<CorrectionRequest> findPending();

    /** 荷役作業に紐づく申請の履歴（新しい順）。<strong>却下も残す。</strong> */
    List<CorrectionRequest> findByHandlingActivityId(long handlingActivityId);

    /**
     * 予約に紐づく申請（Booking への ACL が使う。C8）。
     *
     * <p><strong>承認待ちを先に、申請の新しい順。</strong> 営業担当者が知りたいのは
     * 「いま止まっている話があるか」であり、決着した話はその後でよい。
     *
     * @param bookingId 予約 ID。<strong>形式が違えば空を返す</strong>
     *                  （予約詳細を開いただけで 500 にしない）
     */
    List<CorrectionRequest> findByBookingId(UUID bookingId);

    /** 承認待ちの件数（ダッシュボードのカード。ADR-014）。 */
    int countPending();

    /**
     * 承認待ちの申請を持つ予約 ID（IT13 レビュー C4）。
     *
     * <p><strong>1 件ずつ聞かない。</strong> 一覧の行数だけ問い合わせが飛ぶ。
     */
    List<UUID> findBookingIdsWithPendingCorrection(java.util.Collection<UUID> bookingIds);
}
