package com.example.bookingms.application.internal.commandservices;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 経路割当コマンド（アプリケーション層 DTO）
 * interfaces 層への依存を持たないようにするために定義する
 */
public record RouteCargoCommand(List<LegData> legs) {

    /**
     * 旅程区間データ
     */
    public record LegData(
            String voyageNumber,
            String loadLocationUnlocode,
            String unloadLocationUnlocode,
            LocalDateTime loadTime,
            LocalDateTime unloadTime
    ) {}
}
