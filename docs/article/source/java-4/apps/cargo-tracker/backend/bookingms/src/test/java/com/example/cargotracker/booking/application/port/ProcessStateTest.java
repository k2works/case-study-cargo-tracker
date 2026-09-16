package com.example.cargotracker.booking.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.port.ProcessState.Status;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 連鎖の途中経過そのものの判断。 */
class ProcessStateTest {

    private static final Instant NOW = Instant.parse("2026-09-03T09:00:00Z");

    private static ProcessState state(int completed, int total, Status status,
            Map<String, String> metadata) {
        return new ProcessState("BOOKING_TO_TRACKING", "bk-1", "CONFIRMED", total, completed,
                status, metadata, NOW, NOW, status == Status.RUNNING ? null : NOW);
    }

    @Test
    @DisplayName("実行中かどうかを答える")
    void tellsWhetherRunning() {
        assertThat(state(0, 3, Status.RUNNING, Map.of()).isRunning()).isTrue();
        assertThat(state(3, 3, Status.COMPLETED, Map.of()).isRunning()).isFalse();
        assertThat(state(1, 3, Status.COMPENSATED, Map.of()).isRunning()).isFalse();
    }

    @Test
    @DisplayName("段が残っているうちは完了とみなさない")
    void tellsWhetherAllStepsDone() {
        assertThat(state(2, 3, Status.RUNNING, Map.of()).allStepsDone())
                .as("残っているのに完了にすると、抜けに気づけない")
                .isFalse();
        assertThat(state(3, 3, Status.RUNNING, Map.of()).allStepsDone()).isTrue();
    }

    @Test
    @DisplayName("metadata を渡さなくても壊れない")
    void toleratesMissingMetadata() {
        assertThat(state(0, 3, Status.RUNNING, null).metadata()).isEmpty();
    }

    @Test
    @DisplayName("metadata は後から書き換えられない")
    void copiesMetadata() {
        Map<String, String> given = new HashMap<>(Map.of("bookingId", "bk-1"));

        ProcessState state = state(0, 3, Status.RUNNING, given);
        given.put("bookingId", "書き換え");

        assertThat(state.metadata()).containsEntry("bookingId", "bk-1");
    }
}
