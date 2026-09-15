package com.example.cargotracker.simulation.infrastructure.schedule;

import com.example.cargotracker.simulation.application.SimulationScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 頃合いが来たら次を流す（US36 §受入基準 1）。
 *
 * <p><b>時間はここだけが持つ。</b> application 側に置くと、検査が眠ることでしか
 * 判別できなくなる——経過時間のアサートは脆弱な実装に戻しても緑になる。</p>
 *
 * <p><b>許可していない環境では糸も作らない</b>（§6）。「動いている稼働が無いから
 * 何もしない」に頼ると、設定を戻した瞬間に流れ始める。</p>
 *
 * <p><b>間隔は設定から受ける。</b> {@code application.yml} で
 * {@code ${ENV:default}} として明示的に受けているので、環境変数名を推測させない。</p>
 */
@Component
@ConditionalOnProperty(prefix = "cargo-tracker.simulation.schedule", name = "enabled",
        havingValue = "true")
public class SimulationScheduleTicker {

    private static final Logger log = LoggerFactory.getLogger(SimulationScheduleTicker.class);

    private final SimulationScheduleService schedules;

    public SimulationScheduleTicker(SimulationScheduleService schedules) {
        this.schedules = schedules;
    }

    /**
     * 次を流すか決める。
     *
     * <p><b>握りつぶす。</b> 1 回の失敗で糸を止めると、以後この稼働は誰にも
     * 動かせなくなる（止める操作も届かない）。記録して次の頃合いを待つ。</p>
     */
    @Scheduled(fixedDelayString = "${cargo-tracker.simulation.schedule.interval:30s}")
    public void tick() {
        try {
            schedules.tick();
        } catch (RuntimeException e) {
            log.warn("継続実行の頃合いで例外が出た（次の頃合いを待つ）", e);
        }
    }
}
