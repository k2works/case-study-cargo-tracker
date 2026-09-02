package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.booking.domain.model.valueobjects.Email;
import com.example.cargotracker.booking.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * 荷主（UC02 / US02）。
 *
 * <p>不変条件（domain-model.md「Shipper 集約」）:</p>
 * <ul>
 *   <li>CORPORATE は契約番号が必須</li>
 *   <li>INDIVIDUAL は法人契約を持てない</li>
 *   <li>Email の形は値オブジェクトが守る。一意は集約 1 つでは守れないので三段で守る</li>
 * </ul>
 */
@EventSourced(idType = String.class, tagKey = "shipperId")
public class Shipper {

    private String shipperId;
    private ShipperType shipperType;
    private Email email;
    private CorporateContract corporateContract;

    @EntityCreator
    public Shipper() {
        // Axon がイベント再生で呼ぶ。
    }

    @CommandHandler
    public static String register(RegisterShipperCommand command, EventAppender appender) {
        validate(command);
        CorporateContract contract = command.corporateContract();
        appender.append(new ShipperRegisteredEvent(
                command.shipperId(),
                command.shipperType().name(),
                command.name(),
                command.email().value(),
                command.phone(),
                command.address(),
                contract == null ? null : contract.contractNumber(),
                contract == null ? null : contract.discountRate().value().toPlainString()));
        return command.shipperId();
    }

    private static void validate(RegisterShipperCommand command) {
        if (command.shipperId() == null || command.shipperId().isBlank()) {
            throw new IllegalArgumentException("荷主 ID は必須です");
        }
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("荷主名は必須です");
        }
        if (command.shipperType() == null) {
            throw new IllegalArgumentException("荷主種別は必須です");
        }
        if (command.shipperType() == ShipperType.CORPORATE && command.corporateContract() == null) {
            throw new IllegalArgumentException("法人は契約番号が必須です");
        }
        if (command.shipperType() == ShipperType.INDIVIDUAL && command.corporateContract() != null) {
            throw new IllegalArgumentException("個人は法人契約を持てません");
        }
    }

    @EventSourcingHandler
    void on(ShipperRegisteredEvent event) {
        this.shipperId = event.shipperId();
        this.shipperType = ShipperType.valueOf(event.shipperType());
        this.email = new Email(event.email());
        this.corporateContract = null;
    }
}
