package com.example.cargotracker.simulation.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.application.SimulationScheduleService;
import com.example.cargotracker.simulation.infrastructure.query.SimulationScheduleQueries;
import com.example.cargotracker.simulation.infrastructure.query.SimulationScheduleQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 継続実行の入口（S94 / US36 §受入基準 3・4・8）。 */
class SimulationScheduleControllerTest {

    private static final Instant AT = Instant.parse("2026-09-15T01:00:00Z");

    private final AtomicBoolean stopped = new AtomicBoolean();
    private SimulationScheduleQueries.ScheduleView view;

    private SimulationScheduleController controller() {
        SimulationScheduleService schedules =
                new SimulationScheduleService(null, null, null, null) {

                    @Override
                    public String start(Long seed, String startedBy) {
                        return "SCH-" + (seed == null ? "auto" : seed) + "-" + startedBy;
                    }

                    @Override
                    public void stop() {
                        stopped.set(true);
                    }
                };
        SimulationScheduleQueryHandler queries =
                new SimulationScheduleQueryHandler(schedules, null) {

                    @Override
                    public SimulationScheduleQueries.ScheduleView findActive() {
                        return view;
                    }
                };
        return new SimulationScheduleController(schedules, queries);
    }

    @Test
    @DisplayName("US36 §4: 始めると稼働の識別子を返す（画面が S94 へ移る）")
    void startsAndReturnsTheScheduleId() {
        var response = controller().start("admin01",
                new SimulationScheduleController.StartRequest(42L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().scheduleId()).isEqualTo("SCH-42-admin01");
    }

    @Test
    @DisplayName("US36 §3: 本文が無くても始められる（種は任意）")
    void startsWithoutABody() {
        var response = controller().start("admin01", null);

        assertThat(response.getBody().scheduleId())
                .as("**指定しなければ作って記録する**——記録しないと再現できない")
                .isEqualTo("SCH-auto-admin01");
    }

    @Test
    @DisplayName("US36 §4: 止めると受け付けたことを返す（止まりきったかは S94 が出す）")
    void acceptsTheStop() {
        var response = controller().stop();

        assertThat(response.getStatusCode())
                .as("**すぐには止まらない。** 走っている実行は最後まで終える")
                .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(stopped).isTrue();
    }

    @Test
    @DisplayName("US36 §8: 動いていれば稼働と統計を返す")
    void returnsTheActiveSchedule() {
        view = new SimulationScheduleQueries.ScheduleView("SCH-1", 42L, 30, 2,
                new BigDecimal("0.20"), "RUNNING", "実行中", "admin01", AT, null, 1,
                List.of(new SimulationScheduleQueries.CountView("SUCCEEDED", "成功", 3)),
                List.of());

        var response = controller().active();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().seed()).isEqualTo(42L);
    }

    @Test
    @DisplayName("動いていなければ 204（画面が「動いていません」を出す）")
    void returnsNoContentWhenNothingIsActive() {
        assertThat(controller().active().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
