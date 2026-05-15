package com.example.cargotracker.routingms.domain.model.aggregates;

import com.example.cargotracker.routingms.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routingms.domain.model.commands.UpdateVoyageScheduleCommand;
import com.example.cargotracker.routingms.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routingms.domain.model.events.VoyageScheduleUpdatedEvent;
import com.example.cargotracker.routingms.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routingms.domain.model.valueobjects.UnLocode;
import com.example.cargotracker.routingms.domain.model.valueobjects.VoyageStatus;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 航海 Aggregate（US24 / US25）。
 *
 * <p>ADR-0007 採用パターン（Axon Framework 5.1 Event Sourcing）で実装。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>登録直後は {@link VoyageStatus#SCHEDULED}</li>
 *   <li>同一 voyage_number で 2 回目の登録は Axon イベントストアで重複検知</li>
 * </ul>
 */
@EventSourced(idType = String.class, tagKey = "voyageNumber")
@Profile("!springboot-integration-test")
public final class Voyage {

    private String voyageNumber;
    private Carrier carrier;
    private String shipName;
    private UnLocode origin;
    private UnLocode destination;
    private LocalDateTime departureDate;
    private LocalDateTime arrivalDate;
    private List<CarrierMovement> carrierMovements = new ArrayList<>();
    private List<CargoType> acceptedCargoTypes = new ArrayList<>();
    private VoyageStatus status;

    @EntityCreator
    public Voyage() {
        // Axon が Event 再生で呼び出すデフォルトコンストラクタ。
        // ADR-0007 必須: コレクション型のフィールドはここで初期化する。
    }

    /**
     * 航海スケジュール新規登録（作成系コマンド）。
     *
     * <p>ADR-0007 推奨パターン: 作成系 Command は {@code static} メソッドとして実装し、
     * {@link EventAppender} を引数で受け取る。</p>
     */
    @CommandHandler
    public static String register(RegisterVoyageCommand command, EventAppender appender) {
        appender.append(new VoyageRegisteredEvent(
                command.voyageNumber(),
                command.carrier(),
                command.shipName(),
                command.origin(),
                command.destination(),
                command.departureDate(),
                command.arrivalDate(),
                List.copyOf(command.carrierMovements()),
                List.copyOf(command.acceptedCargoTypes())));
        return command.voyageNumber();
    }

    @EventSourcingHandler
    public void on(VoyageRegisteredEvent event) {
        this.voyageNumber = event.voyageNumber();
        this.carrier = event.carrier();
        this.shipName = event.shipName();
        this.origin = event.origin();
        this.destination = event.destination();
        this.departureDate = event.departureDate();
        this.arrivalDate = event.arrivalDate();
        this.carrierMovements = new ArrayList<>(event.carrierMovements());
        this.acceptedCargoTypes = new ArrayList<>(event.acceptedCargoTypes());
        this.status = VoyageStatus.SCHEDULED;
    }

    /**
     * 航海スケジュール更新（US25 / UC19）。
     *
     * <p>Carrier と shipName は更新対象外。出発・到着日時、寄港地リスト、対応貨物種別を
     * 上書きする。差分確認とキャンセル動作（受入条件 2 / 5）は UI 側で扱い、ドメインは
     * 確定済みの更新内容を受け取ってイベントを発行する。</p>
     */
    @CommandHandler
    public void updateSchedule(UpdateVoyageScheduleCommand command, EventAppender appender) {
        appender.append(new VoyageScheduleUpdatedEvent(
                command.voyageNumber(),
                command.departureDate(),
                command.arrivalDate(),
                List.copyOf(command.carrierMovements()),
                List.copyOf(command.acceptedCargoTypes())));
    }

    @EventSourcingHandler
    public void on(VoyageScheduleUpdatedEvent event) {
        this.departureDate = event.departureDate();
        this.arrivalDate = event.arrivalDate();
        this.carrierMovements = new ArrayList<>(event.carrierMovements());
        this.acceptedCargoTypes = new ArrayList<>(event.acceptedCargoTypes());
        // status は SCHEDULED 維持（DEPARTED / ARRIVED 後の更新可否は IT4 以降で検討）
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public Carrier getCarrier() {
        return carrier;
    }

    public String getShipName() {
        return shipName;
    }

    public UnLocode getOrigin() {
        return origin;
    }

    public UnLocode getDestination() {
        return destination;
    }

    public LocalDateTime getDepartureDate() {
        return departureDate;
    }

    public LocalDateTime getArrivalDate() {
        return arrivalDate;
    }

    public List<CarrierMovement> getCarrierMovements() {
        return List.copyOf(carrierMovements);
    }

    public List<CargoType> getAcceptedCargoTypes() {
        return List.copyOf(acceptedCargoTypes);
    }

    public VoyageStatus getStatus() {
        return status;
    }
}
