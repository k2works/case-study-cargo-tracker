package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.commands.UpdateVoyageScheduleCommand;
import com.example.cargotracker.routing.domain.model.events.VoyageCancelledEvent;
import com.example.cargotracker.routing.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routing.domain.model.events.VoyageScheduleUpdatedEvent;
import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * 航海（UC19 / US24）。
 *
 * <p>不変条件（domain-model.md「Voyage 集約の不変条件」）:</p>
 * <ul>
 *   <li>1: {@code VoyageNumber} は不変。同一番号の再登録は三段で拒否（ここが 1 段目）</li>
 *   <li>2: 寄港地は時刻昇順で港が連結（{@code Schedule} が守る）</li>
 *   <li>3: 到着は出発より後（{@code CarrierMovement} が守る）</li>
 *   <li>4: {@code acceptedCargoTypes} が空なら一般貨物のみ</li>
 *   <li>5: キャンセル済みの航海は更新できない</li>
 * </ul>
 */
@EventSourced(idType = String.class, tagKey = "voyageNumber")
public class Voyage {

    private String voyageNumber;
    private boolean cancelled;

    @EntityCreator
    public Voyage() {
        // Axon がイベント再生で呼ぶ。
    }

    /**
     * 航海を登録する。
     *
     * <p><b>static ではなくインスタンスのハンドラにしている。</b> static（作る側）と
     * インスタンス（既にある側）を両方置くと、集約が既に存在しても static のほうが
     * 呼ばれ、2 度目の登録が通ってしまう（IT2 に {@code Cargo} で実測）。
     * {@code @EntityCreator} が空の集約を用意するので、インスタンス側だけで両方を扱える。</p>
     */
    @CommandHandler
    public String register(RegisterVoyageCommand command, EventAppender appender) {
        if (voyageNumber != null) {
            // 不変条件 1 の 1 段目。同時登録のレースはここを素通りするので、
            // 投影の UNIQUE と attention_item が残りの 2 段を担う。
            throw new IllegalTransition("航海 " + voyageNumber + " は既に登録されています");
        }
        validate(command);

        appender.append(new VoyageRegisteredEvent(
                command.voyageNumber(),
                command.carrier().carrierCode(),
                command.carrier().carrierName(),
                command.vesselName().value(),
                command.schedule().movements().stream()
                        .map(Voyage::toMovement)
                        .toList(),
                CargoType.resolveAcceptedNames(command.acceptedCargoTypes()),
                command.registeredBy()));
        return command.voyageNumber();
    }

    /**
     * スケジュールを更新する（US25）。
     *
     * <p><b>登録と同じ検査を通す。</b> 更新用に検査を書き直すと「登録では断るのに
     * 更新では通る」が生まれる。値オブジェクトと {@link #validate} の 1 か所だけが
     * 判断を持ち、画面にも投影にも置かない。</p>
     */
    @CommandHandler
    public void updateSchedule(UpdateVoyageScheduleCommand command, EventAppender appender) {
        if (voyageNumber == null) {
            throw new IllegalTransition(
                    "航海 " + command.voyageNumber() + " は登録されていません");
        }
        if (cancelled) {
            // 不変条件 5。キャンセルした航海を直せると、経路候補に「走らない船」が戻る。
            throw new IllegalTransition("航海 " + voyageNumber + " はキャンセル済みです");
        }
        validate(command.voyageNumber(), command.carrier(), command.vesselName(),
                command.schedule());

        appender.append(new VoyageScheduleUpdatedEvent(
                command.voyageNumber(),
                command.carrier().carrierCode(),
                command.carrier().carrierName(),
                command.vesselName().value(),
                command.schedule().movements().stream()
                        .map(Voyage::toUpdatedMovement)
                        .toList(),
                CargoType.resolveAcceptedNames(command.acceptedCargoTypes()),
                command.updatedBy()));
    }

    private static VoyageRegisteredEvent.Movement toMovement(CarrierMovement movement) {
        return new VoyageRegisteredEvent.Movement(
                movement.departure().unLocode().value(),
                movement.arrival().unLocode().value(),
                movement.departureTime(),
                movement.arrivalTime());
    }

    private static VoyageScheduleUpdatedEvent.Movement toUpdatedMovement(
            CarrierMovement movement) {
        return new VoyageScheduleUpdatedEvent.Movement(
                movement.departure().unLocode().value(),
                movement.arrival().unLocode().value(),
                movement.departureTime(),
                movement.arrivalTime());
    }

    private static void validate(RegisterVoyageCommand command) {
        validate(command.voyageNumber(), command.carrier(), command.vesselName(),
                command.schedule());
    }

    private static void validate(String voyageNumber, Carrier carrier, VesselName vesselName,
            Schedule schedule) {
        // 値オブジェクトを通す。ここで文字列のまま空白だけを見ていると、
        // 長さの規則は VoyageNumber に書いてあるのに本番経路に載らない。
        // 20 文字を超える番号は集約を素通りしてイベントになり、投影の
        // voyage_number VARCHAR(20) で落ちて Processing Group が止まる。
        new VoyageNumber(voyageNumber);
        if (carrier == null) {
            throw new BusinessRuleViolation("運送会社は必須です");
        }
        if (vesselName == null) {
            throw new BusinessRuleViolation("船名は必須です");
        }
        if (schedule == null) {
            throw new BusinessRuleViolation("寄港地を 1 件以上入力してください");
        }
    }

    @EventSourcingHandler
    void on(VoyageRegisteredEvent event) {
        this.voyageNumber = event.voyageNumber();
        this.cancelled = false;
    }

    @EventSourcingHandler
    void on(VoyageScheduleUpdatedEvent event) {
        this.voyageNumber = event.voyageNumber();
    }

    @EventSourcingHandler
    void on(VoyageCancelledEvent event) {
        this.cancelled = true;
    }

    /** 復元した航海番号。登録済みかどうかの判断に使う。 */
    public String voyageNumber() {
        return voyageNumber;
    }

    /** キャンセル済みか（不変条件 5）。 */
    public boolean cancelled() {
        return cancelled;
    }
}
