package com.example.cargotracker.simulation.application;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.simulation.domain.model.aggregates.SimulationSchedule;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScheduleStatus;
import com.example.cargotracker.simulation.infrastructure.config.SimulationScheduleProperties;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationScheduleMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

/**
 * 継続実行の稼働を受け付ける（US36）。
 *
 * <p><b>時間を持たない。</b> 「次を流す頃合いか」は外から {@link #tick} で
 * 叩かれる。時間を内側に持つと、検査が眠ることでしか判別できなくなる
 * ——経過時間のアサートは脆弱な実装に戻しても緑になる（IT7 の教訓）。</p>
 *
 * <p><b>稼働は記憶に持たない。</b> 状態は DB が正で、毎回読み直す。プロセスが
 * 落ちても「実行中のまま誰も止められない稼働」が残らない。</p>
 */
public class SimulationScheduleService {

    private static final Logger log = LoggerFactory.getLogger(SimulationScheduleService.class);

    private final SimulationScheduleMapper schedules;
    private final SimulationService runs;
    private final SimulationScheduleProperties properties;
    private final Clock clock;

    public SimulationScheduleService(SimulationScheduleMapper schedules, SimulationService runs,
            SimulationScheduleProperties properties, Clock clock) {
        this.schedules = schedules;
        this.runs = runs;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 稼働を始める（US36 §受入基準 4）。
     *
     * <p><b>許可された環境でだけ始める</b>（§6）。実行そのものの許可とは別の段
     * ——流し続ける側は業務を止めうる。</p>
     *
     * @param seed 乱数の種。<b>指定しなければ時刻から作って記録する</b>（§3）
     *     ——記録しないと、あとから同じ並びを再現できない
     * @return 始めた稼働の識別子
     */
    public String start(Long seed, String startedBy) {
        if (!properties.enabled()) {
            throw new BusinessRuleViolation(
                    "この環境では継続実行を開始できません"
                            + "（流し続ける側が業務を止めないようにするため）");
        }
        long actualSeed = seed == null ? clock.instant().toEpochMilli() : seed;
        // **36 文字に収める。** 列は VARCHAR(36)（接頭辞 + UUID は 40 文字になる）。
        String scheduleId = "SCH-" + UUID.randomUUID().toString().replace("-", "");
        SimulationSchedule schedule = SimulationSchedule.start(scheduleId, actualSeed,
                properties.interval(), properties.maxConcurrent(),
                properties.exceptionRatio(), startedBy, clock.instant());
        try {
            schedules.insert(toRow(schedule));
        } catch (DuplicateKeyException e) {
            // **稼働は 1 本。** 読んでから書くまでの隙間で、もう 1 本が始まった。
            // 500 にせず、利用者の言葉に翻訳する（IT16 のレビュー N9 と同じ形）。
            var active = schedules.findActive();
            throw new IllegalTransition("継続実行はすでに動いています"
                    + (active == null ? "" : "（稼働 " + active.scheduleId() + "）"), e);
        }
        return scheduleId;
    }

    /**
     * 稼働を止める（US36 §受入基準 4）。
     *
     * <p><b>すぐに停止中にしない。</b> 走っている実行は最後まで終える。
     * 決着は {@link #tick} が見る。</p>
     */
    public void stop() {
        SimulationSchedule schedule = active();
        schedule.stop(clock.instant());
        persist(schedule);
    }

    /**
     * 頃合いが来たので、次を流すか決める（US36 §受入基準 1・2・4）。
     *
     * <p><b>上限は集約が判定する。</b> ここで数えて分岐すると、判定が 2 か所に
     * 住む——本番と検査が別の判定を持つのと同じ形になる。</p>
     *
     * @return 始めた実行の識別子。始めなかったときは {@code null}
     */
    public String tick() {
        var row = schedules.findActive();
        if (row == null) {
            return null;
        }
        SimulationSchedule schedule = restore(row);
        int running = schedules.countRunning(schedule.scheduleId());
        if (!schedule.canStartAnother(running)) {
            // 止める途中なら、決着したかを見る。
            schedule.settleIfDrained(running, clock.instant());
            if (schedule.status().isStopped()) {
                persist(schedule);
            }
            return null;
        }
        ScenarioInput input = schedule.nextScenario();
        try {
            return runs.start(input, schedule.scheduleId(), schedule.startedBy());
        } catch (IllegalTransition e) {
            // **そのシナリオが走っている。** 同じシナリオを同時に 1 本という
            // 守り（US33 §5）は継続実行でも外さない——外すと、手で流した実行と
            // 突き合わせられなくなる。次の頃合いにまた引く。
            log.debug("いま流せないシナリオを引いた: scenario={}", input.scenario(), e);
            return null;
        }
    }

    /** いま動いている稼働。<b>無ければ断る</b>（止める先が無い）。 */
    public SimulationSchedule active() {
        var row = schedules.findActive();
        if (row == null) {
            throw new IllegalTransition("動いている継続実行がありません");
        }
        return restore(row);
    }

    /** いま動いている稼働（無ければ {@code null}）。<b>読み口はこちらを使う</b>。 */
    public SimulationSchedule activeOrNull() {
        var row = schedules.findActive();
        return row == null ? null : restore(row);
    }

    private void persist(SimulationSchedule schedule) {
        schedules.updateStatus(schedule.scheduleId(), schedule.status().name(),
                schedule.stoppedAt(), clock.instant());
    }

    private SimulationScheduleMapper.ScheduleRow toRow(SimulationSchedule schedule) {
        return new SimulationScheduleMapper.ScheduleRow(schedule.scheduleId(), schedule.seed(),
                (int) schedule.interval().toSeconds(), schedule.maxConcurrent(),
                schedule.exceptionRatio(), schedule.status().name(), schedule.startedBy(),
                schedule.startedAt(), schedule.stoppedAt(), clock.instant());
    }

    /**
     * 記録から稼働を組み直す。
     *
     * <p><b>種から同じ位置に戻らない。</b> 組み直すたびに乱数は先頭から始まるので、
     * 再起動をまたぐと同じ条件がもう一度流れる。<b>再現できるのは「同じ種を
     * 指定して始め直したとき」であって、稼働の続きではない</b>（注 N4）。</p>
     */
    private SimulationSchedule restore(SimulationScheduleMapper.ScheduleRow row) {
        SimulationSchedule schedule = SimulationSchedule.start(row.scheduleId(), row.seed(),
                java.time.Duration.ofSeconds(row.intervalSeconds()), row.maxConcurrent(),
                row.exceptionRatio(), row.startedBy(), row.startedAt());
        if (ScheduleStatus.valueOf(row.status()) == ScheduleStatus.STOPPING) {
            schedule.stop(row.stoppedAt() == null ? Instant.EPOCH : row.stoppedAt());
        }
        return schedule;
    }
}
