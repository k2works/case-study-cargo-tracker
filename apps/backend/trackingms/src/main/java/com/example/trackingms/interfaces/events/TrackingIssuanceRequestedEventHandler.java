package com.example.trackingms.interfaces.events;

import com.example.shared.events.TrackingIssuanceRequestedEvent;
import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.domain.services.TrackingNumberGenerator;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 追跡発行依頼イベントの受信ハンドラ（US14 / cross-service、ADR-0009 / ADR-0011）。
 *
 * <p>bookingms の {@code BookingSagaManager} が発行する {@link TrackingIssuanceRequestedEvent} を
 * Kafka 経由で購読し、{@code TrackingNumberGenerator} で採番した
 * {@link com.example.trackingms.domain.model.TrackingNumber} を引数に
 * {@link InitializeTrackingCommand} を発行する。Cargo 集約相当の {@code TrackingActivity} 集約が
 * 新規生成され、初期状態 {@code NOT_RECEIVED} で起動する。</p>
 *
 * <p>{@code tracking-issuance-requests} プロセッシンググループとして、Kafka（cargo-events）の
 * {@link org.axonframework.extensions.kafka.eventhandling.consumer.streamable.StreamableKafkaMessageSource}
 * を source とする tracking プロセッサに割り当てる（{@code KafkaEventProcessingConfig}）。</p>
 *
 * <p><b>例外ハンドリング方針（ホワイトリスト、ADR-0011）</b>: 冪等スキップする例外は
 * {@link AggregateNotFoundException} と {@link CommandExecutionException} の 2 種のみ。
 * それ以外の例外は握り潰さず伝播させ、tracking プロセッサのエラーハンドラ
 * （再試行・可視化）に委ねる。catch 範囲を不用意に広げる退行を起こさないため、
 * 新たに冪等スキップを許容したい例外型が現れた場合は本クラスの Javadoc と
 * {@code TrackingIssuanceRequestedEventHandlerTest} のネガティブテストを同時に更新すること。</p>
 */
@Component
@ProcessingGroup("tracking-issuance-requests")
public class TrackingIssuanceRequestedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(TrackingIssuanceRequestedEventHandler.class);

    private final CommandGateway commandGateway;
    private final TrackingNumberGenerator trackingNumberGenerator;

    public TrackingIssuanceRequestedEventHandler(CommandGateway commandGateway,
                                                 TrackingNumberGenerator trackingNumberGenerator) {
        this.commandGateway = commandGateway;
        this.trackingNumberGenerator = trackingNumberGenerator;
    }

    @EventHandler
    public void on(TrackingIssuanceRequestedEvent event) {
        String trackingNumber = trackingNumberGenerator.generate().value();
        try {
            commandGateway.sendAndWait(new InitializeTrackingCommand(trackingNumber, event.bookingId()));
        } catch (AggregateNotFoundException ex) {
            // 同じ trackingNumber で集約が（再生時に）既に存在しないとされた場合。
            // 通常は採番が乱数なので発生しないが、replay 時のレース等で起きうる。冪等にスキップ。
            log.warn("追跡初期化をスキップしました。対象集約が存在しません（bookingId={}, trackingNumber={}）。"
                    + "再生された古いイベントの可能性があります。", event.bookingId(), trackingNumber);
        } catch (CommandExecutionException ex) {
            // 既に初期化済みでガード違反した場合（同一 bookingId に対する再生・重複配信）。
            // 冪等にスキップする。
            log.warn("追跡初期化コマンドの実行をスキップしました（bookingId={}, trackingNumber={}）: {}",
                    event.bookingId(), trackingNumber, ex.getMessage());
        }
    }
}
