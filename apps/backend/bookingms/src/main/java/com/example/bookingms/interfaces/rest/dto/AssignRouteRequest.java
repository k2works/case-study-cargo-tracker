package com.example.bookingms.interfaces.rest.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 経路割当リクエスト DTO
 *
 * <p>routingms が返す経路候補の時刻は {@code timestamptz} 由来のためオフセット付き ISO 8601
 * （例: {@code 2030-01-02T17:00:00+09:00}）で送られてくる。bookingms ドメインは {@code LocalDateTime}
 * を扱うため、interfaces 層では {@link OffsetDateTime} で受け取り、Controller で
 * {@code toLocalDateTime()} に変換して内部コマンドへ橋渡しする。
 */
public record AssignRouteRequest(List<LegRequest> legs) {

    /**
     * 旅程区間リクエスト DTO
     */
    public record LegRequest(
            String voyageNumber,
            String loadLocationUnlocode,
            String unloadLocationUnlocode,
            OffsetDateTime loadTime,
            OffsetDateTime unloadTime
    ) {}
}
