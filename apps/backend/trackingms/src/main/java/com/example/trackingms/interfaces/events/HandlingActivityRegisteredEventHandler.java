package com.example.trackingms.interfaces.events;

import com.example.shared.events.HandlingActivityRegisteredEvent;
import com.example.trackingms.domain.commands.UpdateTransportStatusCommand;
import com.example.trackingms.domain.model.TransportStatus;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * handlingms → trackingms クロスサービスハンドラ（US15・US16 / IT5 3.3）。
 *
 * <p>shared モジュールの {@link HandlingActivityRegisteredEvent} を Kafka tracking モードで受信し、
 * 荷役種別に応じた {@link UpdateTransportStatusCommand} を {@code TrackingActivity} 集約に送信する。</p>
 *
 * <h2>HandlingType と TransportStatus の対応（US15 受入条件）</h2>
 * <ul>
 *   <li>RECEIVE → RECEIVED</li>
 *   <li>LOAD → LOADED</li>
 *   <li>UNLOAD → UNLOADED</li>
 *   <li>CLAIM → DELIVERED（US16）</li>
 *   <li>CUSTOMS → 状態変更なし（IT6 で扱う）</li>
 * </ul>
 *
 * <h2>エラーハンドリング方針（ADR-0011 / ホワイトリスト方式）</h2>
 * <p>{@link AggregateNotFoundException}（追跡 Aggregate が未初期化）と
 * {@link CommandExecutionException}（不正な状態遷移など、IllegalStateException ラップ）の 2 種のみ
 * WARN ログを出してスキップする。それ以外は伝播させて Tracking Processor のリトライに任せる。</p>
 *
 * <p>不正遷移（例: UNLOADED → DELIVERED は AWAITING_CLAIM を経由しない）は CommandExecutionException
 * となり、本ハンドラは WARN スキップする。フロント側で AWAITING_CLAIM への手動更新を別途行う必要が
 * ある（US17 の手動更新と組合せる業務フロー）。</p>
 */
@Component
@ProcessingGroup("handling-activity-events")
public class HandlingActivityRegisteredEventHandler {

    private static final Logger log =
            LoggerFactory.getLogger(HandlingActivityRegisteredEventHandler.class);

    /** HandlingType（shared では String）→ TransportStatus の対応マップ。 */
    private static final Map<String, TransportStatus> HANDLING_TO_STATUS = handlingMap();

    private final CommandGateway commandGateway;

    public HandlingActivityRegisteredEventHandler(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(HandlingActivityRegisteredEvent event) {
        TransportStatus to = HANDLING_TO_STATUS.get(event.handlingType());
        if (to == null) {
            log.info("[handling-activity-events] type={} は本イテレーションでは状態更新なし（trackingNumber={}）",
                    event.handlingType(), event.trackingNumber());
            return;
        }
        UpdateTransportStatusCommand command = new UpdateTransportStatusCommand(
                event.trackingNumber(),
                to,
                event.unlocode(),
                event.voyageNumber(),
                event.occurredAt(),
                "荷役 " + event.handlingType() + " による自動状態更新"
        );
        try {
            commandGateway.sendAndWait(command);
        } catch (AggregateNotFoundException e) {
            // ADR-0011: トラッキング Aggregate が未初期化（採番前）の場合は WARN スキップ。
            log.warn("[handling-activity-events] trackingNumber={} の Aggregate が未初期化のためスキップ "
                            + "(activityId={}, type={})",
                    event.trackingNumber(), event.activityId(), event.handlingType());
        } catch (CommandExecutionException e) {
            // ADR-0011: 集約が拒否（不正な状態遷移など）した場合は WARN スキップ。
            log.warn("[handling-activity-events] trackingNumber={} への状態更新 ({} → {}) が拒否されました: {}",
                    event.trackingNumber(), event.handlingType(), to,
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }
    }

    private static Map<String, TransportStatus> handlingMap() {
        Map<String, TransportStatus> m = new java.util.HashMap<>();
        m.put("RECEIVE", TransportStatus.RECEIVED);
        m.put("LOAD", TransportStatus.LOADED);
        m.put("UNLOAD", TransportStatus.UNLOADED);
        m.put("CLAIM", TransportStatus.DELIVERED);
        // CUSTOMS は状態変更なし
        return Map.copyOf(m);
    }
}
