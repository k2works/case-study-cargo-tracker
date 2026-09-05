package com.example.cargotracker.routing.infrastructure.query;

import com.example.cargotracker.routing.domain.model.valueobjects.VoyageSearchCriteria;
import java.time.Instant;
import java.util.List;

/** 航海の読み取りモデル（domain-model.md「クエリ一覧」）。 */
public final class RoutingQueries {

    private RoutingQueries() {
    }

    public record FindVoyageQuery(String voyageNumber) {
    }

    /**
     * 一覧（S32）。
     *
     * <p>{@code includeFinished} は「出港済み・キャンセルも表示」の操作に対応する。
     * 既定を false にしているのは、出港してしまった便が混ざると一覧全体が
     * 「これから使える航海」として信用されなくなるため（ui_design.md）。</p>
     *
     * <p>{@code cargoType} は US05 の絞り込み。危険物・冷凍の予約は、その種別を
     * 受け入れる航海だけが候補になる。</p>
     */
    public record FindVoyagesQuery(int page, int size, boolean includeFinished,
            VoyageSearchCriteria criteria) {

        public FindVoyagesQuery {
            // 条件なしを null で表さない。null のまま渡すと、条件を読む側が
            // 「条件が無い」と「条件が空」を別々に扱うことになり、片方で落ちる。
            criteria = criteria == null
                    ? VoyageSearchCriteria.of(null, null, null, null, null)
                    : criteria;
        }
    }

    /** 画面に出す航海。 */
    public record VoyageView(
            String voyageNumber,
            String carrierCode,
            String carrierName,
            String vesselName,
            String departureUnLocode,
            String arrivalUnLocode,
            Instant departureAt,
            Instant arrivalAt,
            boolean cancelled,
            List<String> acceptedCargoTypes,
            List<MovementView> movements,
            // 最終更新（US25）。一度も更新していなければ null。
            // 変更内容の履歴は Event Store が持つ（投影には持たせない）。
            Instant updatedAt,
            String updatedBy) {
    }

    /** 寄港地。並び順そのものが業務の意味を持つ。 */
    public record MovementView(
            int movementSeq,
            String departureUnLocode,
            String arrivalUnLocode,
            Instant departureAt,
            Instant arrivalAt) {
    }

    public record VoyageListView(List<VoyageView> items, int total) {
    }
}
