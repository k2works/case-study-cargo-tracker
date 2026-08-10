package com.example.cargotracker.booking.application.internal.queryservices;

import java.util.List;
import java.util.Optional;

/**
 * キャンセル申請の読み取り（US30。CQRS のクエリ側）。
 *
 * <p>実装はインフラ層に置く（ArchUnit ルール 3）。
 */
public interface CancellationQueryService {

    /**
     * 決着していない申請（<strong>古い順</strong>。承認待ち一覧）。
     *
     * <p><strong>待たせている申請から捌く。</strong>
     */
    List<CancellationView> findPending();

    /** 申請 1 件（承認の画面）。<strong>陸揚げ地の候補を含む。</strong> */
    Optional<CancellationView> findById(long id);

    /**
     * 予約に紐づく申請（<strong>新しい順</strong>。予約詳細の履歴）。
     *
     * <p><strong>却下も残す</strong> — 却下したことも経緯である。
     */
    List<CancellationView> findByBookingId(String bookingId);

    /** 決着していない申請の件数（ダッシュボードのカード。ADR-014）。 */
    int countPending();
}
