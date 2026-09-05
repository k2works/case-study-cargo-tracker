package com.example.cargotracker.shared.contract.query;

import java.util.List;

/**
 * 経路候補の応答（US08）。
 *
 * <p><b>候補のリストだけを返さない。</b> 打ち切りに当たったことを一緒に返さないと、
 * 「上限まで探した」と「候補が無い」が同じ見え方になる（ADR-0007）。
 * 経路設計者は、条件を変えても直らないものを変え続けることになる。</p>
 *
 * @param candidates 推奨順の候補。0 件でも失敗ではない
 * @param truncated 探索の上限（乗り継ぎ回数・候補数）で切ったか
 */
public record RouteCandidatesResponse(List<RouteCandidateDto> candidates, boolean truncated) {

    public RouteCandidatesResponse {
        candidates = List.copyOf(candidates);
    }
}
