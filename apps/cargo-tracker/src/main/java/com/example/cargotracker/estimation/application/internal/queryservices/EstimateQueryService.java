package com.example.cargotracker.estimation.application.internal.queryservices;

import java.util.List;

/** 見積の読み取り（CQRS のクエリ側）。 */
public interface EstimateQueryService {

    /**
     * 見積の一覧（新しい順）。
     *
     * <p><strong>期限切れの判定は読み出しのたびに行う</strong>
     * （`domain-model.md` のビジネスルール 7。ADR-019 と同じ形）。
     */
    List<EstimateSummaryView> findAll();
}
