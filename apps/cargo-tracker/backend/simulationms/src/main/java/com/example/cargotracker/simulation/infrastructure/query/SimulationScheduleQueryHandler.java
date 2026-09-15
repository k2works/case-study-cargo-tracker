package com.example.cargotracker.simulation.infrastructure.query;

import com.example.cargotracker.simulation.application.SimulationScheduleService;
import com.example.cargotracker.simulation.domain.model.aggregates.SimulationSchedule;
import com.example.cargotracker.simulation.domain.model.valueobjects.RunStatus;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationScheduleMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 継続実行の読み口（S94 / US36 §受入基準 3・8）。
 *
 * <p><b>呼び名は列挙が持つ。</b> 画面が対応表を持つと、値を足したときに片方だけが
 * 古くなる——<b>列挙に値を足したら全箇所を回る</b>。</p>
 */
@Component
public class SimulationScheduleQueryHandler {

    private final SimulationScheduleService schedules;
    private final SimulationScheduleMapper mapper;

    public SimulationScheduleQueryHandler(SimulationScheduleService schedules,
            SimulationScheduleMapper mapper) {
        this.schedules = schedules;
        this.mapper = mapper;
    }

    /**
     * いちばん新しい稼働と統計。<b>一度も動かしていなければ {@code null}</b>。
     *
     * <p><b>止めた稼働も返す</b>（US36 §3・§8）。止めた瞬間に件数も失敗工程の
     * 分布も乱数の種も読めなくなると、夜通し流して翌朝に結果を読む使い方が
     * 成り立たない（IT17 のレビューで指摘）。</p>
     */
    public SimulationScheduleQueries.ScheduleView findActive() {
        SimulationSchedule schedule = schedules.latestOrNull();
        if (schedule == null) {
            return null;
        }
        String scheduleId = schedule.scheduleId();
        return new SimulationScheduleQueries.ScheduleView(scheduleId, schedule.seed(),
                (int) schedule.interval().toSeconds(), schedule.maxConcurrent(),
                schedule.exceptionRatio(), schedule.status().name(),
                schedule.status().label(), schedule.startedBy(), schedule.startedAt(),
                schedule.stoppedAt(), mapper.countRunning(scheduleId),
                toViews(mapper.countByStatus(scheduleId),
                        code -> RunStatus.valueOf(code).label()),
                toViews(mapper.countFailedStepsByKind(scheduleId),
                        code -> StepKind.valueOf(code).label()));
    }

    private static List<SimulationScheduleQueries.CountView> toViews(
            List<SimulationScheduleMapper.CountRow> rows,
            java.util.function.UnaryOperator<String> labelOf) {
        return rows.stream()
                .map(row -> new SimulationScheduleQueries.CountView(row.status(),
                        labelOf.apply(row.status()), row.count()))
                .toList();
    }
}
