package com.example.simulationms.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.model.valueobjects.SessionStatus;
import com.example.simulationms.domain.model.valueobjects.Seed;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 継続実行のセッション（US37-4・[ADR-031] 決定 4）。
 *
 * <p><strong>停止は新規の開始だけを止める。</strong>進行中の実行を中断すると、
 * 業務データが中途半端な状態で残る——[ADR-030] 決定 5（巻き戻さない）と
 * 噛み合わない。
 *
 * <p>「止めた」と「止まった」を分けるために {@code STOPPING} を置く。分けないと、
 * 進行中の実行が残っているのに停止済みと表示され、<strong>統計が確定していない
 * 状態で読まれる</strong>。
 */
@DisplayName("継続実行のセッション")
class ContinuousRunSessionTest {

    private static final Instant STARTED = Instant.parse("2026-12-07T01:00:00Z");

    private static final ContinuousRunPolicy POLICY =
            ContinuousRunPolicy.of(30, 3, BigDecimal.valueOf(0.2));

    private static ContinuousRunSession started() {
        return ContinuousRunSession.start(SessionId.of("SES-20261207-0001"),
                Seed.of(42L), POLICY, "admin01", STARTED);
    }

    @Nested
    @DisplayName("開始")
    class Starting {

        @Test
        @DisplayName("開始した直後は実行中で、種と上限を覚えている")
        void remembersTheSeedAndPolicy() {
            ContinuousRunSession session = started();

            assertThat(session.status()).isEqualTo(SessionStatus.RUNNING);
            assertThat(session.seed()).isEqualTo(Seed.of(42L));
            assertThat(session.policy()).isEqualTo(POLICY);
            assertThat(session.startedBy()).isEqualTo("admin01");
            assertThat(session.stoppedAt()).isEmpty();
        }

        @Test
        @DisplayName("実行中は、上限に達していなければ開始してよい")
        void allowsStartingWhileRunning() {
            assertThat(started().canStartAnotherRun(0)).isTrue();
            assertThat(started().canStartAnotherRun(2)).isTrue();
            assertThat(started().canStartAnotherRun(3)).isFalse();
        }
    }

    @Nested
    @DisplayName("停止")
    class Stopping {

        /** <strong>止めた瞬間に、新規の開始だけが止まる。</strong> */
        @Test
        @DisplayName("停止を指示すると、進行中があるうちは停止処理中になる")
        void becomesStoppingWhileRunsAreStillGoing() {
            ContinuousRunSession stopping = started().stop(1, STARTED.plusSeconds(60));

            assertThat(stopping.status()).isEqualTo(SessionStatus.STOPPING);
            assertThat(stopping.canStartAnotherRun(0)).isFalse();
            assertThat(stopping.stoppedAt()).isEmpty();
        }

        /** 進行中が無ければ、その場で止まる。 */
        @Test
        @DisplayName("進行中が無ければ、停止の指示でそのまま停止する")
        void stopsImmediatelyWhenNothingIsRunning() {
            ContinuousRunSession stopped = started().stop(0, STARTED.plusSeconds(60));

            assertThat(stopped.status()).isEqualTo(SessionStatus.STOPPED);
            assertThat(stopped.stoppedAt()).contains(STARTED.plusSeconds(60));
        }

        /**
         * <strong>進行中が終わって初めて停止済みになる。</strong>
         * ここを飛ばすと、統計が確定していない状態で「停止しました」と読まれる。
         */
        @Test
        @DisplayName("停止処理中は、進行中が尽きたときに停止済みへ移る")
        void settlesWhenTheLastRunFinishes() {
            ContinuousRunSession stopping = started().stop(2, STARTED.plusSeconds(60));

            assertThat(stopping.settleIfFinished(1, STARTED.plusSeconds(90)).status())
                    .isEqualTo(SessionStatus.STOPPING);
            ContinuousRunSession stopped =
                    stopping.settleIfFinished(0, STARTED.plusSeconds(120));
            assertThat(stopped.status()).isEqualTo(SessionStatus.STOPPED);
            assertThat(stopped.stoppedAt()).contains(STARTED.plusSeconds(120));
        }

        /** 実行中でないものは、進行中が尽きても状態を変えない。 */
        @Test
        @DisplayName("実行中のセッションは、進行中が 0 でも勝手に停止しない")
        void doesNotStopARunningSessionJustBecauseNothingIsInFlight() {
            assertThat(started().settleIfFinished(0, STARTED.plusSeconds(90)).status())
                    .isEqualTo(SessionStatus.RUNNING);
        }

        @Test
        @DisplayName("停止済みのセッションは、もう一度止められない")
        void cannotStopTwice() {
            ContinuousRunSession stopped = started().stop(0, STARTED.plusSeconds(60));

            assertThatThrownBy(() -> stopped.stop(0, STARTED.plusSeconds(90)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("停止");
        }
    }
}
