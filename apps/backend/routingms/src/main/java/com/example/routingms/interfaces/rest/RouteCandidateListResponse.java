package com.example.routingms.interfaces.rest;

import com.example.routingms.application.internal.FindRouteCandidatesUseCase;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 経路候補の一覧（US08・[ADR-017]）。
 *
 * <p>候補が 0 件でも 200 で返す。「無い」は正常な結果であり、404 ではない。
 * <strong>そのとき何で絞ったかを返す</strong>ため、画面は「どの条件が効いているか」を示し、
 * 条件を緩める操作を出せる。件数だけ伝えて終わりにしない。
 *
 * @param appliedCriteria 実際に使った条件。画面が送らなかった既定値（積み替えの上限）も含む
 */
public record RouteCandidateListResponse(
        List<RouteCandidateResponse> candidates,
        int totalCount,
        AppliedCriteria appliedCriteria) {

        public RouteCandidateListResponse {
        // 受け取った一覧を写して持つ。呼び出し元が渡したものをそのまま抱えると、
        // 渡したあとの書き換えがこちらの中身を変える。null は許す——項目が無いことと
        // 空であることは違う
        candidates = candidates == null ? null : List.copyOf(candidates);
        }


    /** 実際に使った条件。到着期限は業務タイムゾーンでの当日終わりに直したもの。 */
    public record AppliedCriteria(
            String originUnLocode,
            String originName,
            String destinationUnLocode,
            String destinationName,
            Instant arrivalDeadline,
            String cargoType,
            int maxTransshipments,
            /**
             * 出発希望日（US10）。指定が無ければ {@code null}。
             *
             * <p>候補が 0 件だったときに「何が効いているか」を示すために返す。返さないと、
             * 経路設計者は期限だけを緩め続けることになる。
             */
            Instant earliestDeparture) {
    }

    public static RouteCandidateListResponse from(FindRouteCandidatesUseCase.Result result) {
        AtomicInteger rank = new AtomicInteger(1);
        List<RouteCandidateResponse> candidates = result.candidates().stream()
                .map(path -> RouteCandidateResponse.from(path, rank.getAndIncrement()))
                .toList();

        var specification = result.specification();
        return new RouteCandidateListResponse(candidates, candidates.size(),
                new AppliedCriteria(
                        specification.origin().unLocode(), specification.origin().name(),
                        specification.destination().unLocode(), specification.destination().name(),
                        specification.arrivalDeadline(), specification.cargoType().name(),
                        specification.maxTransshipments(), specification.earliestDeparture()));
    }
}
