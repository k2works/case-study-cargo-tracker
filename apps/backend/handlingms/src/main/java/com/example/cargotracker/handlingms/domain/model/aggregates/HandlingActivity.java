package com.example.cargotracker.handlingms.domain.model.aggregates;

import com.example.cargotracker.handlingms.domain.model.commands.RegisterHandlingActivityCommand;
import com.example.cargotracker.handlingms.domain.model.events.HandlingActivityRegisteredEvent;
import com.example.cargotracker.handlingms.domain.model.events.UnexpectedHandlingDetectedEvent;
import com.example.cargotracker.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handlingms.domain.model.valueobjects.ClaimVerification;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.handlingms.domain.model.valueobjects.VoyageNumber;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.springframework.context.annotation.Profile;

/**
 * 荷役作業 Aggregate（US15 / US16）。
 *
 * <p>Axon Framework 5.1 の Event Sourcing パターン。bookingms の {@code Cargo} と同方針。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>{@link HandlingType#LOAD} / {@link HandlingType#UNLOAD} は {@link VoyageNumber} 必須（Command で検証）</li>
 *   <li>{@link HandlingType#CLAIM} は {@link ClaimVerification} 必須（Command で検証・US16）</li>
 *   <li>{@link CargoSnapshot#isExpectedHandling} が {@code false} の場合、{@link UnexpectedHandlingDetectedEvent} を追加発行</li>
 * </ul>
 *
 * <p>関連 ADR: ADR-0012 handlingms と trackingms の責務分離</p>
 */
@EventSourced(idType = String.class, tagKey = "activityId")
@Profile("!springboot-integration-test")
public final class HandlingActivity {

    private String activityId;
    private TrackingNumber trackingNumber;
    private HandlingType handlingType;
    private Location location;
    private boolean unexpected;

    @EntityCreator
    public HandlingActivity() {
        // Axon が Event 再生で呼び出すデフォルトコンストラクタ。
    }

    /**
     * 荷役作業登録（作成系コマンド）。
     *
     * <p>ADR-0007 推奨パターン: 作成系 Command は {@code static} メソッドとして実装し、
     * {@link EventAppender} を引数で受け取る。</p>
     *
     * @param command   登録コマンド
     * @param snapshot  追跡番号から引当した CargoSnapshot（ACL 経由で取得済み）
     * @param appender  Event Appender
     * @return 発番された activityId
     */
    public static String register(
            RegisterHandlingActivityCommand command,
            CargoSnapshot snapshot,
            EventAppender appender) {

        boolean isUnexpected = !snapshot.isExpectedHandling(command.handlingType(), command.location());

        appender.append(new HandlingActivityRegisteredEvent(
                command.activityId(),
                command.trackingNumber(),
                command.handlingType(),
                command.location(),
                command.occurredAt(),
                command.voyageNumber(),
                command.operatorId(),
                command.claimVerification(),
                snapshot,
                isUnexpected));

        if (isUnexpected) {
            appender.append(new UnexpectedHandlingDetectedEvent(
                    command.activityId(),
                    command.trackingNumber(),
                    command.handlingType(),
                    command.location(),
                    snapshot.origin(),
                    snapshot.destination()));
        }

        return command.activityId();
    }

    @EventSourcingHandler
    public void on(HandlingActivityRegisteredEvent event) {
        this.activityId = event.activityId();
        this.trackingNumber = event.trackingNumber();
        this.handlingType = event.handlingType();
        this.location = event.location();
        this.unexpected = event.unexpected();
    }

    public String getActivityId() {
        return activityId;
    }

    public TrackingNumber getTrackingNumber() {
        return trackingNumber;
    }

    public HandlingType getHandlingType() {
        return handlingType;
    }

    public Location getLocation() {
        return location;
    }

    public boolean isUnexpected() {
        return unexpected;
    }
}
