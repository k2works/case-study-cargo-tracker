package com.example.billingms.interfaces.events;

import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.domain.model.TransportRecord;
import com.example.billingms.infrastructure.outboundservices.BillingContextAcl;
import com.example.billingms.infrastructure.outboundservices.BillingContextInfo;
import com.example.shared.events.CargoDeliveredEvent;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * trackingms → billingms クロスサービスハンドラ（US21 / IT7 タスク 2.4、ADR-0012 / ADR-0015）。
 *
 * <p>shared モジュールの {@link CargoDeliveredEvent} を Kafka tracking モードで受信し、
 * {@link BillingContextAcl} で Billing 算出に必要な情報を集めて
 * {@link CalculateInvoiceCommand} を発行する。Invoice 集約が PENDING → CALCULATED に
 * 遷移し、{@code InvoiceCalculatedEvent} が集約発火される（ADR-0012）。</p>
 *
 * <h2>エラーハンドリング方針（ADR-0011 / ホワイトリスト方式）</h2>
 *
 * <p>{@link CommandExecutionException}（Aggregate Identifier 重複や不正状態）は WARN ログを
 * 出してスキップする。これは event store リプレイで CargoDeliveredEvent が再生された場合、
 * または Kafka at-least-once 配信で重複受信した場合に、同じ {@code bookingId} に対して
 * 二度目の Invoice 生成試行を防ぐためのフェイルセーフ。</p>
 *
 * <p>冪等性は集約レベルでは AxonFramework が AggregateIdentifierAlreadyExistsException として
 * 弾く。本ハンドラは bookingId → invoiceId の決定的マッピング（UUID v5 風）を行わず、新規
 * UUID を生成するため、リプレイで invoiceId が変わる。これは Read Model 側の
 * {@code UNIQUE(booking_id)} 制約で重複を防ぐ。同 booking_id への 2 件目の Invoice 投影は
 * DB UNIQUE 違反 → CommandExecutionException → 本ハンドラの WARN スキップで吸収される。</p>
 */
@Component
@ProcessingGroup("cross-billing")
public class CrossCargoDeliveredEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CrossCargoDeliveredEventHandler.class);

    private final CommandGateway commandGateway;
    private final BillingContextAcl billingContextAcl;

    public CrossCargoDeliveredEventHandler(CommandGateway commandGateway,
                                           BillingContextAcl billingContextAcl) {
        this.commandGateway = commandGateway;
        this.billingContextAcl = billingContextAcl;
    }

    @EventHandler
    public void on(CargoDeliveredEvent event) {
        BillingContextInfo info = billingContextAcl.loadFor(event.bookingId(), event.trackingNumber());

        TransportRecord transport = new TransportRecord(
                info.distanceKm(),
                info.weightKg(),
                info.cargoType(),
                info.handlingCount(),
                info.currency()
        );

        String invoiceId = UUID.randomUUID().toString();
        CalculateInvoiceCommand command = new CalculateInvoiceCommand(
                invoiceId,
                event.bookingId(),
                info.shipperId(),
                transport
        );

        try {
            commandGateway.sendAndWait(command);
            log.info(
                    "[cross-billing] Invoice 算出依頼を発行 invoiceId={} bookingId={} basicAmount=(計算中)",
                    invoiceId, event.bookingId());
        } catch (CommandExecutionException e) {
            // ADR-0011 ホワイトリスト方式：bookingId 重複 / 不正状態は WARN スキップ
            log.warn(
                    "[cross-billing] CalculateInvoiceCommand 発行を冪等スキップ bookingId={} cause={}",
                    event.bookingId(), e.getMessage());
        }
    }
}
