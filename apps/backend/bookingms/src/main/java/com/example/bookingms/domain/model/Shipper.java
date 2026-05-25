package com.example.bookingms.domain.model;

import com.example.bookingms.domain.commands.RegisterShipperCommand;
import com.example.bookingms.domain.events.ShipperRegisteredEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

/**
 * 荷主集約（US02 / Booking Context）。
 *
 * <p>個人荷主・法人荷主の共通骨格を担う Aggregate Root。
 * 法人特有の契約番号・割引率は US03 で別 Command として拡張する。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>{@code shipperId} は非 null かつ空文字列禁止</li>
 *   <li>{@code email} は非 null かつ空文字列禁止（一意性は Read Model 側で検証）</li>
 *   <li>{@code shipperType} は非 null</li>
 * </ul>
 */
@Aggregate
public class Shipper {

    @AggregateIdentifier
    private String shipperId;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private ShipperType shipperType;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private String email;

    protected Shipper() {
        // Axon required no-arg constructor
    }

    @CommandHandler
    public Shipper(RegisterShipperCommand command) {
        if (command.shipperId() == null || command.shipperId().isBlank()) {
            throw new IllegalArgumentException("荷主 ID は必須です");
        }
        if (command.shipperType() == null) {
            throw new IllegalArgumentException("荷主種別は必須です");
        }
        if (command.email() == null || command.email().isBlank()) {
            throw new IllegalArgumentException("メールアドレスは必須です");
        }
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("氏名/社名は必須です");
        }
        AggregateLifecycle.apply(new ShipperRegisteredEvent(
                command.shipperId(),
                command.shipperType(),
                command.name(),
                command.addressLine1(),
                command.addressLine2(),
                command.city(),
                command.countryCode(),
                command.postalCode(),
                command.email(),
                command.phone()
        ));
    }

    @EventSourcingHandler
    public void on(ShipperRegisteredEvent event) {
        this.shipperId = event.shipperId();
        this.shipperType = event.shipperType();
        this.email = event.email();
    }

    public String getShipperId() { return shipperId; }
}
