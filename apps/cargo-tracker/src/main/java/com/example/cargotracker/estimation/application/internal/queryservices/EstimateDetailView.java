package com.example.cargotracker.estimation.application.internal.queryservices;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 見積詳細（US01 の受入基準 3）。
 *
 * @param estimateId 見積番号
 * @param route      経路
 * @param cargo      貨物の仕様
 * @param deadline   希望到着期限
 * @param status     見積の状態
 * @param candidates ルート候補（推奨順）
 * @param noCandidateNote 候補が 0 件だった理由の案内。候補があれば空文字
 */
public record EstimateDetailView(
        String estimateId,
        EstimateSummaryView.Route route,
        EstimateSummaryView.Cargo cargo,
        LocalDate deadline,
        EstimateSummaryView.Status status,
        List<Candidate> candidates,
        String noCandidateNote) {

    public EstimateDetailView {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /**
     * ルート候補 1 件。
     *
     * @param voyageNumber 航海番号。<strong>実在する便である</strong>（ADR-023）
     * @param transitLabel 経由港。直行は「直行」
     * @param transitDays  所要日数
     * @param cost         概算費用
     * @param currency     通貨
     */
    public record Candidate(
            String voyageNumber,
            String transitLabel,
            int transitDays,
            BigDecimal cost,
            String currency) { }

    /** 候補があるか。<strong>0 件の理由は画面が説明する。</strong> */
    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    /**
     * この見積で予約に進めるか。
     *
     * <p><strong>期限切れの見積からは進めない</strong>（`ui_design.md`）。
     * 画面の出し分けは本述語をそのまま呼ぶ。
     */
    public boolean bookable() {
        return !status.expired();
    }
}
