package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.commands.MarkBookingSettledCommand;
import com.example.shared.events.PaymentRecordedEvent;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * billingms の {@link PaymentRecordedEvent}（shared）を購読して Cargo 集約を SETTLED に
 * 遷移させる cross-service EventHandler（US23、IT7 T4.5）。
 *
 * <p>{@code @ProcessingGroup("cross-booking-billing")} は ADR-0014/0016 命名規約の
 * {@code cross-} prefix。Kafka の {@code cargo-events} トピックを tracking モードで購読する
 * （{@code KafkaEventProcessingConfig} で登録）。</p>
 *
 * <p><b>例外ハンドリング方針（ADR-0011 ホワイトリスト）</b>: 冪等スキップする例外は
 * {@link AggregateNotFoundException} と {@link CommandExecutionException} の 2 種のみ。
 * Cargo 集約側で SETTLED / CANCELLED は内部冪等スキップする。</p>
 */
@Component
@ProcessingGroup("cross-booking-billing")
public class CrossBillingPaymentHandler {

    private static final Logger log = LoggerFactory.getLogger(CrossBillingPaymentHandler.class);

    private final CommandGateway commandGateway;

    public CrossBillingPaymentHandler(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(PaymentRecordedEvent event) {
        try {
            commandGateway.sendAndWait(new MarkBookingSettledCommand(event.bookingId()));
            log.info("[cross-booking-billing] Cargo 精算済遷移 bookingId={} invoiceId={}",
                    event.bookingId(), event.invoiceId());
        } catch (AggregateNotFoundException ex) {
            // 古い event store 状態 / 不整合での tracking 再生時の冪等スキップ
            log.warn("[cross-booking-billing] SETTLED 遷移スキップ。対象予約が存在しません bookingId={}",
                    event.bookingId());
        } catch (CommandExecutionException ex) {
            // 集約側の遷移制約違反等（基本的に Cargo 側で冪等吸収済み）
            log.warn("[cross-booking-billing] SETTLED コマンドの実行をスキップしました bookingId={}: {}",
                    event.bookingId(), ex.getMessage());
        }
    }
}
