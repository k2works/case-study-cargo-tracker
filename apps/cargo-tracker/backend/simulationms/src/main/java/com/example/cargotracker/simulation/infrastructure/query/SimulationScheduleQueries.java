package com.example.cargotracker.simulation.infrastructure.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 継続実行の読み口（S94 / US36 §受入基準 3・8）。 */
public final class SimulationScheduleQueries {

    private SimulationScheduleQueries() {
    }

    /**
     * 区分ごとの件数（US36 §受入基準 8）。
     *
     * <p><b>US34 と同じ形で出す。</b> 実行結果（S93）が工程を「呼び名 + 値」で
     * 出しているので、統計も同じ読み方にする——画面ごとに読み方が違うと、
     * 同じ数字でも突き合わせられない。</p>
     */
    public record CountView(String code, String label, int count) {
    }

    /**
     * 継続実行のいまと統計（S94）。
     *
     * <p><b>動いていなければ {@code null}</b>——画面が「動いていません」を出す。</p>
     *
     * @param seed 乱数の種。<b>画面で読める</b>（§3）——読めないと再現できない
     */
    public record ScheduleView(
            String scheduleId,
            long seed,
            int intervalSeconds,
            int maxConcurrent,
            BigDecimal exceptionRatio,
            String status,
            String statusLabel,
            String startedBy,
            Instant startedAt,
            Instant stoppedAt,
            int runningNow,
            List<CountView> runsByStatus,
            List<CountView> failuresByStep) {
    }
}
