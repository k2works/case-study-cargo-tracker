package com.example.routingms.interfaces.rest.dto;

/**
 * 経路候補の選択・確定リクエスト（US09 / US11）。
 *
 * <p>経路設計ワークベンチ（S14）が選択した候補の推奨順番号（1 始まり）を指定する。
 * 経路候補は永続化しないため、番号で再算出済みの候補を指す。</p>
 */
public record SelectRouteRequest(int sequence) {
}
