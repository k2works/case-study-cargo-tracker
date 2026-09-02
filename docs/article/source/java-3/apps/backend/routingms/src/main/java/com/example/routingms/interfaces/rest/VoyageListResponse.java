package com.example.routingms.interfaces.rest;

import java.util.List;

/**
 * 一覧の応答。
 *
 * @param voyages 上限までの航海
 * @param totalCount 条件に合う総数（上限で切る前）
 * @param limit 適用した上限
 * @param truncated 上限で切ったか。切ったことを画面が黙ると、経路設計者は
 *     「条件に合う航海はこれで全部だ」と読む
 */
public record VoyageListResponse(
        List<VoyageResponse> voyages, int totalCount, int limit, boolean truncated) {

        public VoyageListResponse {
        // 受け取った一覧を写して持つ。呼び出し元が渡したものをそのまま抱えると、
        // 渡したあとの書き換えがこちらの中身を変える。null は許す——項目が無いことと
        // 空であることは違う
        voyages = voyages == null ? null : List.copyOf(voyages);
        }

}
