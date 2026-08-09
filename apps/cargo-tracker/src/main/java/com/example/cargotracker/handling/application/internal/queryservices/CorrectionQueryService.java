package com.example.cargotracker.handling.application.internal.queryservices;

import java.util.List;

/**
 * 訂正・取り消し申請の読み取り（US36。CQRS のクエリ側）。
 *
 * <p>実装はインフラ層に置く（ArchUnit ルール 3）。
 */
public interface CorrectionQueryService {

    /**
     * 承認待ちの申請（追跡管理者の待ち行列）。
     *
     * <p><strong>古い順に返す。</strong> 待たせている申請から片づける。
     */
    List<CorrectionRequestView> findPending();

    /** 承認待ちの件数（ダッシュボードのカード。ADR-014）。 */
    int countPending();
}
