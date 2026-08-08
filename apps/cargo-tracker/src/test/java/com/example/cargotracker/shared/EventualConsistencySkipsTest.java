package com.example.cargotracker.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import com.example.cargotracker.support.LogCapture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 結果整合の取りこぼしを<strong>数えられる形</strong>で残すことを確かめる（IT6 追補 A1）。
 *
 * <p>ADR-009 で BC 間の伝播をドメインイベントに変えた結果、購読側の失敗を
 * <strong>利用者の画面に返せなくなった</strong>。同期のときは「他の操作が先に
 * 行われました」と出せていたものが、いまはログにしか残らない。
 *
 * <p><strong>ログに出すだけでは「誰も見ない場所に置いた」のと同じである。</strong>
 * 件数として数えられて初めて、閾値を決めて気づくことができる。
 */
@DisplayName("結果整合の取りこぼし")
class EventualConsistencySkipsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final EventualConsistencySkips skips = new EventualConsistencySkips(registry);

    @Test
    void 反映できなかった件数が購読者と理由ごとに数えられる() {
        skips.record("booking", "CONFLICTED", "b-1");
        skips.record("booking", "CONFLICTED", "b-2");
        skips.record("tracking", "NOT_FOUND", "TRK-1");

        assertThat(count("booking", "CONFLICTED")).isEqualTo(2.0);
        assertThat(count("tracking", "NOT_FOUND")).isEqualTo(1.0);
        assertThat(count("tracking", "CONFLICTED")).isZero();
    }

    /**
     * <strong>数えるだけでは原因を追えない。</strong> どのレコードが取りこぼされたかが
     * 分からないと、件数が増えても手当てのしようがない。件数は気づくため、
     * ログは直すためにある。
     */
    @Test
    void 取りこぼした対象がログから特定できる() {
        try (LogCapture log = LogCapture.of(EventualConsistencySkips.class.getName())) {
            skips.record("tracking", "CONFLICTED", "TRK-20260401-0042");

            assertThat(log.messages())
                    .anySatisfy(message -> assertThat(message)
                            .contains("tracking")
                            .contains("CONFLICTED")
                            .contains("TRK-20260401-0042"));
        }
    }

    /** 取りこぼしが無い間は系列そのものが存在してよい（0 件であることが読めればよい）。 */
    @Test
    void 何も起きていなければ件数は増えない() {
        assertThat(count("booking", "CONFLICTED")).isZero();
    }

    private double count(String subscriber, String reason) {
        var counter = registry.find(EventualConsistencySkips.METRIC_NAME)
                .tag("subscriber", subscriber)
                .tag("reason", reason)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
