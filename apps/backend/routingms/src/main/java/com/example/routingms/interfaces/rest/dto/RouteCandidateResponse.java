package com.example.routingms.interfaces.rest.dto;

import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.domain.model.RouteLeg;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 経路候補の REST レスポンス（US08）。
 *
 * <p>経路設計ワークベンチ（S14）が経由港・所要日数・概算費用・航海番号・推奨フラグを表示し、
 * {@code sequence} を選択キー（US09）として用いる。{@code recommended} は推奨順の先頭候補に付与する。</p>
 */
public record RouteCandidateResponse(
        int sequence,
        List<RouteLegResponse> legs,
        List<String> voyageNumbers,
        List<String> ports,
        int estimatedDays,
        BigDecimal estimatedCost,
        String currency,
        boolean direct,
        boolean recommended) {

    /**
     * 経路候補を 1 件のレスポンスに写像する。
     *
     * @param candidate   経路候補
     * @param sequence    推奨順の 1 始まりの順序（選択キー）
     * @param recommended 推奨候補かどうか（推奨順の先頭に付与）
     */
    public static RouteCandidateResponse from(RouteCandidate candidate, int sequence, boolean recommended) {
        return new RouteCandidateResponse(
                sequence,
                candidate.legs().stream().map(RouteLegResponse::from).toList(),
                candidate.voyageNumbers(),
                ports(candidate),
                candidate.estimatedDays(),
                candidate.estimatedCost(),
                candidate.currency(),
                candidate.isDirect(),
                recommended);
    }

    /** 経由港の列（最初の積込港 + 各区間の荷降し港）。 */
    private static List<String> ports(RouteCandidate candidate) {
        List<String> ports = new ArrayList<>();
        ports.add(candidate.legs().get(0).loadUnlocode());
        for (RouteLeg leg : candidate.legs()) {
            ports.add(leg.unloadUnlocode());
        }
        return ports;
    }
}
