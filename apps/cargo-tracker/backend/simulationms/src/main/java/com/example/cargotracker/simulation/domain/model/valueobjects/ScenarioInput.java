package com.example.cargotracker.simulation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.math.BigDecimal;

/**
 * 1 本の実行に渡す条件（US36 §受入基準 1）。
 *
 * <p><b>種から再現できるのはここまで</b>（注 N4）。実行の時刻と生成される識別子
 * （荷主 ID・予約番号・追跡番号）は<b>種の外</b>である——UUID と採番は乱数が
 * 決めない。この線引きを書かないと「再現できない」と読まれる。</p>
 *
 * <p><b>到着期限は日数で持つ。</b> 日付で持つと、同じ種でも流した日によって
 * 違う値になる——再現できるのは「期限までの日数」までである。</p>
 */
public record ScenarioInput(
        Scenario scenario,
        String originUnLocode,
        String destinationUnLocode,
        String cargoType,
        BigDecimal weightKg,
        int arrivalDeadlineDays) {

    public ScenarioInput {
        if (originUnLocode == null || originUnLocode.equals(destinationUnLocode)) {
            // 業務が必ず断る組を作ると、確かめたい経路を通らないまま
            // 「失敗」だけが積み上がる。
            throw new BusinessRuleViolation("出発地と目的地は別の港である必要があります");
        }
    }
}
