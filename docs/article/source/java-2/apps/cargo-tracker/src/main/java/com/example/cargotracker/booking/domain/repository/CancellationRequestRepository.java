package com.example.cargotracker.booking.domain.repository;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.aggregates.CancellationRequest;
import java.util.List;
import java.util.Optional;

/**
 * キャンセル申請の出力ポート（US30）。実装はインフラ層に置く（DIP）。
 */
public interface CancellationRequestRepository {

    /** 新しい申請を保存し、採番された ID を返す。 */
    long save(CancellationRequest request);

    /**
     * 決定（承認・却下）を保存する（楽観的ロック付き）。
     *
     * @return 更新できたか。<strong>0 件は「別の担当者が先に決めた」ことを表す</strong>
     */
    boolean update(CancellationRequest request);

    Optional<CancellationRequest> findById(long id);

    /**
     * 決着していない申請（<strong>古い順</strong>。承認待ち一覧）。
     *
     * <p><strong>待たせている申請から捌く。</strong> 新しい順に並べると、
     * 古い申請がいつまでも下に残る。
     */
    List<CancellationRequest> findPending();

    /** 決着していない申請の件数（ダッシュボードのカード。ADR-014）。 */
    int countPending();

    /**
     * 予約に紐づく申請（<strong>新しい順</strong>。予約詳細の履歴）。
     *
     * <p><strong>却下も残す</strong> — 却下したことも経緯である。
     */
    List<CancellationRequest> findByBookingId(BookingId bookingId);

    /**
     * 決着していない申請があるか。
     *
     * <p><strong>二重の申請を業務の言葉で拒む。</strong> 部分ユニーク索引でも
     * 防いでいるが、制約に頼ると画面には 500 が出る。
     * <strong>ローカル（H2）では索引が働かない</strong>ため、なおさら要る。
     */
    boolean existsPendingFor(BookingId bookingId);
}
