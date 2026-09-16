package com.example.cargotracker.shared.infrastructure.axon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 退避したイベントを処理し直す入口（[ADR-0014] 決定 1）。
 *
 * <p>実際に退避させて処理し直す検査は {@code DeadLetterQueueIT}（trackingms）にある。
 * ここで見るのは<b>入口の振る舞い</b>——何列処理し直せたかを返すこと、処理し直せなく
 * なったら止まること、際限なく回らないことである。</p>
 */
class DeadLetterRetryEndpointTest {

    /** 指定した回数だけ「処理し直せた」と答え、それ以降は false を返す退避先。 */
    private static SequencedDeadLetterProcessor<?> succeedingTimes(int times) {
        var processor = mock(SequencedDeadLetterProcessor.class);
        var remaining = new int[] {times};
        when(processor.processAny()).thenAnswer(invocation -> {
            boolean processed = remaining[0] > 0;
            remaining[0]--;
            return CompletableFuture.completedFuture(processed);
        });
        return processor;
    }

    private static DeadLetterRetryEndpoint endpointWith(
            Map<String, SequencedDeadLetterProcessor<?>> processors) {
        Configuration configuration = mock(Configuration.class);
        when(configuration.getComponents(any())).thenReturn(new LinkedHashMap<>(processors));
        return new DeadLetterRetryEndpoint(configuration);
    }

    @Test
    @DisplayName("処理し直せた列の数を Processing Group ごとに返す")
    void reportsHowManySequencesWereReprocessed() {
        // **「呼んだ」と「効いた」は違う。** 数を返さないと、原因が直っていない
        // ことに気づけないまま次へ進む。
        var endpoint = endpointWith(Map.of("projection", succeedingTimes(3)));

        Map<String, Object> result = endpoint.retry();

        assertThat(result).extractingByKey("processedSequences")
                .isEqualTo(Map.of("projection", 3));
    }

    @Test
    @DisplayName("まだ直っていなければ 0 列と答える（消さない）")
    void answersZeroWhenNothingCanBeReprocessed() {
        var endpoint = endpointWith(Map.of("projection", succeedingTimes(0)));

        assertThat(endpoint.retry()).extractingByKey("processedSequences")
                .isEqualTo(Map.of("projection", 0));
    }

    @Test
    @DisplayName("際限なく回らない（1 回の呼び出しに上限がある）")
    void stopsAtTheUpperBound() {
        // **止まらない運用タスクは、止め方が分からない。** 退避が大量に溜まって
        // いるときこそ呼ぶものなので、1 回の呼び出しで区切れる形にする。
        var endpoint = endpointWith(Map.of("projection", succeedingTimes(Integer.MAX_VALUE)));

        Map<?, ?> processed = (Map<?, ?>) endpoint.retry().get("processedSequences");

        assertThat((Integer) processed.get("projection")).isEqualTo(200);
    }

    @Test
    @DisplayName("退避先を持たない Processing Group は数えない")
    void skipsProcessorsWithoutADeadLetterQueue() {
        // DLQ が無い Processing Group は null で登録される（Axon の実装）。
        Map<String, SequencedDeadLetterProcessor<?>> processors = new LinkedHashMap<>();
        processors.put("projection", succeedingTimes(1));
        processors.put("reaction", null);
        var endpoint = endpointWith(processors);

        assertThat(endpoint.deadLetterProcessors()).extractingByKey("processors")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.COLLECTION)
                .containsExactly("projection");
    }
}
