package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.commands.RegisterShipperCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CorporateContract;
import com.example.cargotracker.booking.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.booking.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
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
    /**
     * 復元した時点のメールアドレス。値オブジェクトにしないのは、鍵を破棄した荷主では
     * {@code null} が届くためである（ADR-0003）。復元で検査すると、削除要求に応えた
     * 荷主に対する後続のコマンドがすべて失敗する。<b>検査するのは新規受け付け時だけ。</b>
     */
    private String email;
    private CorporateContract corporateContract;

    @EntityCreator
    public Shipper() {
        // Axon がイベント再生で呼ぶ。
    }

    /**
     * 荷主を登録する。
     *
     * <p><b>static ではなくインスタンスのハンドラにする。</b> static（作る側）と
     * インスタンス（既にある側）を両方置くと、集約が既に存在しても static のほうが
     * 呼ばれ、2 度目の登録が通る（[ADR-0001] 決定 5 第 9 項）。</p>
     */
    @CommandHandler
    public String register(RegisterShipperCommand command, EventAppender appender) {
        if (shipperId != null) {
            // 復元した集約が既に登録を持っているのに受け付けると、イベント列に
            // 登録が 2 本並び、どちらが正か決まらない。
            throw new IllegalTransition("荷主 " + shipperId + " は既に登録されています");
        }
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
        // 検査しない。鍵を破棄した荷主では null が届く（ADR-0003）。
        this.email = event.email();
        // 契約はイベントが運んでいる。捨てると、リプレイした集約だけが法人契約を
        // 持たず、契約変更や割引の不変条件を足した瞬間に誤判断する。
        this.corporateContract = event.contractNumber() == null
                ? null
                : new CorporateContract(event.contractNumber(),
                        new DiscountRate(new java.math.BigDecimal(event.discountRate())));
    }

    /** 個人情報が読めるか。読めない＝削除済み。 */
    public boolean isShredded() {
        return email == null;
    }

    /** 復元した荷主の識別子。 */
    public String shipperId() {
        return shipperId;
    }

    /** 復元した荷主種別。契約変更の不変条件で使う。 */
    public ShipperType shipperType() {
        return shipperType;
    }

    /** 復元した法人契約。個人なら空。 */
    public java.util.Optional<CorporateContract> corporateContract() {
        return java.util.Optional.ofNullable(corporateContract);
    }
}
