package com.example.cargotracker.shared.infrastructure.axon;

import java.util.LinkedHashMap;
import java.util.Map;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.deadletter.SequencedDeadLetterProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

/**
 * 退避したイベントを処理し直す入口（[ADR-0014] 決定 1・IT13 引き継ぎ枠 A）。
 *
 * <p><b>直したあとに退避を消すのは「黙って捨てる」ことである。</b> ADR-0014 は
 * 決定 1 で「捨てない」と書いているのに、IT12 の実機確認では {@code DELETE} で
 * 片づけた——<b>処理し直す入口が無かった</b>ためである。開発環境の使い捨て
 * データだったので済んだが、本番では消せない。</p>
 *
 * <p><b>Actuator に置く理由。</b> 業務の API ではないので {@code /api/v1} に
 * 出さない。運用の読み口（{@code projection:dead-letters}）と同じ立場のものを、
 * 同じ入口（{@code /actuator}）に並べる。</p>
 *
 * <p><b>直っていなければ、また退避される。</b> それが正しい——原因を直さずに
 * 処理し直しても通らないことが、ここで分かる。{@code processed} が {@code false}
 * のままなら、退避先に残っている（消えていない）。</p>
 */
@Component
@Endpoint(id = "deadletters")
public class DeadLetterRetryEndpoint {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRetryEndpoint.class);

    /** 1 回の呼び出しで処理し直す列の上限。無限に回らないようにする。 */
    private static final int MAX_SEQUENCES = 200;

    private final Configuration configuration;

    public DeadLetterRetryEndpoint(Configuration configuration) {
        this.configuration = configuration;
    }

    /** 退避先を持つ Processing Group の一覧（処理し直せる先が分かる）。 */
    @ReadOperation
    public Map<String, Object> deadLetterProcessors() {
        return Map.of("processors", processors().keySet());
    }

    /**
     * 退避したイベントを、列ごとに処理し直す。
     *
     * @return Processing Group ごとの「処理し直せた列の数」
     */
    @WriteOperation
    public Map<String, Object> retry() {
        Map<String, Object> processed = new LinkedHashMap<>();
        processors().forEach((name, processor) -> {
            int succeeded = 0;
            while (succeeded < MAX_SEQUENCES && Boolean.TRUE.equals(processor.processAny().join())) {
                succeeded++;
            }
            log.info("退避したイベントを処理し直しました: {} で {} 列", name, succeeded);
            processed.put(name, succeeded);
        });
        return Map.of("processedSequences", processed);
    }

    /**
     * 退避先を持つ処理の component。
     *
     * <p>Axon はこれを Processing Group ごとに
     * {@code EventHandlingComponent[<processor>][<component>]} という名前で登録する。
     * 名前を推測しない——<b>登録されているものを列挙する</b>（推測すると、名前の
     * 付け方が変わった版で黙って 0 件になる）。</p>
     */
    private Map<String, SequencedDeadLetterProcessor<?>> processors() {
        Map<String, SequencedDeadLetterProcessor<?>> found = new LinkedHashMap<>();
        configuration.getComponents(SequencedDeadLetterProcessor.class)
                .forEach((name, processor) -> {
                    if (processor != null) {
                        found.put(name, processor);
                    }
                });
        return found;
    }
}
