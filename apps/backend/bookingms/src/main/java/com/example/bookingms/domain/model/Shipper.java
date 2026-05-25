package com.example.bookingms.domain.model;

import com.example.bookingms.domain.commands.RegisterShipperCommand;
import com.example.bookingms.domain.events.ShipperRegisteredEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;

/**
 * 荷主集約（US02 + US03 / Booking Context）。
 *
 * <p>個人荷主（INDIVIDUAL）と法人荷主（CORPORATE）の両方を扱う集約ルート。
 * 法人荷主の場合のみ {@code contractNumber} / {@code discountRate} を持つ。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>{@code shipperId}・{@code email}・{@code name} は非空</li>
 *   <li>{@code shipperType} は非 null</li>
 *   <li>CORPORATE の場合: {@code contractNumber} 非空、{@code discountRate} 非 null かつ [0.0, 0.3]</li>
 *   <li>INDIVIDUAL の場合: {@code contractNumber} / {@code discountRate} は両方 null</li>
 * </ul>
 */
@Aggregate
public class Shipper {

    private static final BigDecimal MIN_DISCOUNT_RATE = new BigDecimal("0.000");
    private static final BigDecimal MAX_DISCOUNT_RATE = new BigDecimal("0.300");

    @AggregateIdentifier
    private String shipperId;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private ShipperType shipperType;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private String email;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private String contractNumber;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private BigDecimal discountRate;

    protected Shipper() {
        // Axon required no-arg constructor
    }

    @CommandHandler
    public Shipper(RegisterShipperCommand command) {
        validateBasicFields(command);
        validateCorporateContract(command);
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
                command.phone(),
                command.contractNumber(),
                command.discountRate()
        ));
    }

    private void validateBasicFields(RegisterShipperCommand command) {
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
    }

    private void validateCorporateContract(RegisterShipperCommand command) {
        if (command.shipperType() == ShipperType.CORPORATE) {
            if (command.contractNumber() == null || command.contractNumber().isBlank()) {
                throw new IllegalArgumentException("法人荷主の契約番号は必須です");
            }
            if (command.discountRate() == null) {
                throw new IllegalArgumentException("法人荷主の割引率は必須です");
            }
            if (command.discountRate().compareTo(MIN_DISCOUNT_RATE) < 0
                    || command.discountRate().compareTo(MAX_DISCOUNT_RATE) > 0) {
                throw new IllegalArgumentException("割引率は 0.0 以上 0.3 以下である必要があります");
            }
        } else {
            if (command.contractNumber() != null) {
                throw new IllegalArgumentException("個人荷主は契約番号を持てません");
            }
            if (command.discountRate() != null) {
                throw new IllegalArgumentException("個人荷主は割引率を持てません");
            }
        }
    }

    @EventSourcingHandler
    public void on(ShipperRegisteredEvent event) {
        this.shipperId = event.shipperId();
        this.shipperType = event.shipperType();
        this.email = event.email();
        this.contractNumber = event.contractNumber();
        this.discountRate = event.discountRate();
    }

    public String getShipperId() { return shipperId; }
}
