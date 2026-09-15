package com.example.cargotracker.simulation.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.application.SimulationScheduleService;
import com.example.cargotracker.simulation.domain.model.aggregates.SimulationSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 一度も動かしていないときの読み口（S94）。
 *
 * <p><b>共有の表では作れない状態がある。</b> 統合テストの DB には他の検査が
 * 入れた稼働が残るので、「1 本も無い」は実物では作れない——ここだけは
 * 差し替えで確かめる。</p>
 */
class SimulationScheduleQueryHandlerTest {

    @Test
    @DisplayName("一度も動かしていなければ null（画面が「動いていません」を出す）")
    void returnsNullWhenNothingHasEverRun() {
        var service = new SimulationScheduleService(null, null, null, null) {
            @Override
            public SimulationSchedule latestOrNull() {
                return null;
            }
        };

        assertThat(new SimulationScheduleQueryHandler(service, null).findActive()).isNull();
    }
}
