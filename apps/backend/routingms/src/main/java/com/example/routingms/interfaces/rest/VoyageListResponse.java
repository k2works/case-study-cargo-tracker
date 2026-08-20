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
}
