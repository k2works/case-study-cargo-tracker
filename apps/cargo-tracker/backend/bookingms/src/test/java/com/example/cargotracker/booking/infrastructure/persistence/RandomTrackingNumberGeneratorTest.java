package com.example.cargotracker.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 衝突したときの採り直し（ADR-0011 決定 2）。
 *
 * <p>実 DB では衝突をまず起こせない（36^10 通り）。<b>起こせないから確かめない、では
 * 採り直しが壊れても誰も気づかない。</b>「使われているか」の返事だけを差し替えて、
 * ぶつかった先の道を通す。</p>
 */
class RandomTrackingNumberGeneratorTest {

    @Test
    @DisplayName("ぶつかったら別の番号を採り直す")
    void retriesOnCollision() {
        // 最初の 2 つは使用済み、3 つ目で空く。
        RandomTrackingNumberGenerator generator =
                new RandomTrackingNumberGenerator(mapperAnswering(true, true, false));

        assertThat(generator.next()).matches("^TRK-[0-9A-Z]{10}$");
    }

    @Test
    @DisplayName("空きが見つからないまま回り続けない")
    void stopsInsteadOfLoopingForever() {
        // **全部使用済み**という返事しか来ない状況。無限に回るとスレッドが戻らない。
        RandomTrackingNumberGenerator generator =
                new RandomTrackingNumberGenerator(mapperAlwaysUsed());

        assertThatThrownBy(generator::next)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("空き");
    }

    /** {@code trackingNumberExists} の返事だけを順に決めた {@link CargoSummaryMapper}。 */
    private static CargoSummaryMapper mapperAnswering(Boolean... answers) {
        Deque<Boolean> queue = new ArrayDeque<>(java.util.List.of(answers));
        return stub(() -> queue.isEmpty() ? Boolean.FALSE : queue.poll());
    }

    private static CargoSummaryMapper mapperAlwaysUsed() {
        return stub(() -> Boolean.TRUE);
    }

    private static CargoSummaryMapper stub(java.util.function.Supplier<Boolean> exists) {
        return (CargoSummaryMapper) Proxy.newProxyInstance(
                CargoSummaryMapper.class.getClassLoader(),
                new Class<?>[] {CargoSummaryMapper.class},
                (proxy, method, args) -> {
                    if ("trackingNumberExists".equals(method.getName())) {
                        return exists.get();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
