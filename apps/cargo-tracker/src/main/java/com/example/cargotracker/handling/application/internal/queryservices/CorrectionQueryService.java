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

    /**
     * 決定済みを含む最近の申請（US36）。
     *
     * <p><strong>承認待ちだけを出すと、決まった瞬間に一覧から消える。</strong>
     * 申請した荷役作業員には「承認されたのか却下されたのか」も
     * <strong>却下の理由</strong>も届かない。却下に理由を必須にした意味が消える。
     *
     * <p>並びは<strong>承認待ちを先に、申請の古い順</strong>。
     * 追跡管理者にとっては待ち行列であり、待たせている申請から片づける。
     */
    List<CorrectionRequestView> findRecent(int limit);

    /** 承認待ちの件数（ダッシュボードのカード。ADR-014）。 */
    int countPending();
}
