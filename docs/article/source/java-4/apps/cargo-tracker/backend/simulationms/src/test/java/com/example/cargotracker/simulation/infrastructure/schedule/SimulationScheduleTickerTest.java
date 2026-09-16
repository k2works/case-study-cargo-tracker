package com.example.cargotracker.simulation.infrastructure.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.cargotracker.simulation.application.SimulationScheduleService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 頃合いの糸（US36 §受入基準 1）。
 *
 * <p><b>「握りつぶす」はコメントでは守られない</b>（書いた保証は赤で固定する）。
 * 1 回の失敗で糸が止まると、以後その稼働は止める操作すら届かなくなる——
 * 壊して赤になることを、ここで確かめる。</p>
 */
class SimulationScheduleTickerTest {

    private static final class Spy extends SimulationScheduleService {

        private final AtomicInteger calls = new AtomicInteger();
        private final boolean throwing;

        private Spy(boolean throwing) {
            super(null, null, null, null);
            this.throwing = throwing;
        }

        @Override
        public String tick() {
            calls.incrementAndGet();
            if (throwing) {
                throw new IllegalStateException("頃合いで落ちた");
            }
            return null;
        }
    }

    @Test
    @DisplayName("頃合いが来たら、稼働の判断を呼ぶ")
    void delegatesToTheSchedule() {
        Spy service = new Spy(false);

        new SimulationScheduleTicker(service).tick();

        assertThat(service.calls).hasValue(1);
    }

    @Test
    @DisplayName("例外が出ても糸を止めない（次の頃合いも呼ばれる）")
    void keepsTickingAfterAFailure() {
        Spy service = new Spy(true);
        SimulationScheduleTicker ticker = new SimulationScheduleTicker(service);

        assertThatCode(ticker::tick).doesNotThrowAnyException();
        assertThatCode(ticker::tick).doesNotThrowAnyException();

        assertThat(service.calls).hasValue(2);
    }
}
