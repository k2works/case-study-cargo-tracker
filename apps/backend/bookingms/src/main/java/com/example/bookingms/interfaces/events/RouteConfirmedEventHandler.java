package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.commands.AssignRouteToCargoCommand;
import com.example.bookingms.domain.model.Leg;
import com.example.shared.events.RouteConfirmedEvent;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 経路確定イベントの受信ハンドラ（US11 / cross-service、ADR-0009）。
 *
 * <p>routingms が発行する {@link RouteConfirmedEvent} を Kafka 経由で購読し、確定旅程を写し取って
 * {@link AssignRouteToCargoCommand} を発行する。Cargo 集約が {@code CargoRoutedEvent} を適用し、予約状態を
 * 経路提案中（ROUTE_PROPOSED）へ更新、{@code cargo_leg} を確定する。IT3 の bookingms → routingms と
 * 対になる routingms → bookingms 方向の連携。</p>
 *
 * <p>{@code route-confirmed} プロセッシンググループとして、bookingms 内のイベントを処理する default
 * プロセッサ（event store source）から分離し、Kafka（cargo-events）の StreamableKafkaMessageSource を
 * source とする tracking プロセッサに割り当てる（{@code KafkaEventProcessingConfig}）。tracking 再処理・
 * 重複配信に備え、既に経路提案中で割当不可の場合は冪等にスキップする。</p>
 *
 * <p><b>例外ハンドリング方針（ホワイトリスト）</b>: 冪等スキップする例外は
 * {@link AggregateNotFoundException} と {@link CommandExecutionException} の 2 種のみ。
 * それ以外の例外は握り潰さず伝播させ、tracking プロセッサのエラーハンドラ（再試行・可視化）に委ねる。
 * catch 範囲を不用意に広げると、真の基盤障害が黙ってスキップされ可視性を失うため、
 * 新たに冪等スキップを許容したい例外型が現れた場合は本クラスの Javadoc と
 * {@code RouteConfirmedEventHandlerTest} のネガティブテスト（ADR-0010）も同時に更新すること。</p>
 */
@Component
@ProcessingGroup("cross-route-confirmed-events")
public class RouteConfirmedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RouteConfirmedEventHandler.class);

    private final CommandGateway commandGateway;

    public RouteConfirmedEventHandler(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(RouteConfirmedEvent event) {
        List<Leg> legs = event.legs().stream()
                .map(leg -> new Leg(
                        leg.voyageNumber(),
                        leg.loadUnlocode(),
                        leg.unloadUnlocode(),
                        leg.loadTime(),
                        leg.unloadTime()))
                .toList();
        try {
            commandGateway.sendAndWait(new AssignRouteToCargoCommand(event.bookingId(), legs));
        } catch (AggregateNotFoundException ex) {
            // 対象予約が event store に存在しない。古い/再生された cross-service イベント
            // （event store がリセットされた環境での tracking 再生など）では発生し得るため、
            // ERROR で再スローせず冪等にスキップする（tracking プロセッサのブロック・ログ汚染を防ぐ）
            log.warn("経路割当をスキップしました。対象予約が存在しません（bookingId={}）。"
                    + "再生された古いイベントの可能性があります。", event.bookingId());
        } catch (CommandExecutionException ex) {
            // 既に経路提案中等で割当不可（tracking 再処理・重複配信）の場合は冪等にスキップする
            log.warn("経路割当コマンドの実行をスキップしました（bookingId={}）: {}",
                    event.bookingId(), ex.getMessage());
        }
    }
}
