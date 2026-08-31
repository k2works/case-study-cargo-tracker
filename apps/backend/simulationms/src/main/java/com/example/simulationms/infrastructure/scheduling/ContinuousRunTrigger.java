package com.example.simulationms.infrastructure.scheduling;

import com.example.simulationms.application.internal.commandservices.ContinuousRunScheduler;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 継続実行を一定間隔で刻む（[ADR-031] 決定 2）。
 *
 * <p><strong>刻みは simulationms の中にある。</strong>外部のジョブ基盤に出すと、
 * 停止・再現・上限の 3 つが別の場所で管理される。
 *
 * <p>実際に始めてよいかはセッションが決める。ここで判断すると、上限の判定が
 * 2 か所に分かれる。
 */
public class ContinuousRunTrigger {

    private final ContinuousRunScheduler scheduler;

    public ContinuousRunTrigger(ContinuousRunScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Scheduled(fixedDelayString = "${app.simulation.tick-millis:1000}")
    public void tick() {
        scheduler.tick();
    }
}
