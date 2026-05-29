package com.example.handlingms.domain.model;

import com.example.handlingms.domain.commands.RegisterHandlingActivityCommand;
import com.example.handlingms.domain.events.HandlingActivityRegisteredEvent;
import com.example.handlingms.domain.events.UnexpectedHandlingDetectedEvent;
import com.example.handlingms.domain.services.HandlingValidationService;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 荷役活動集約（{@code HandlingActivity}、US15・US16 / IT5 タスク 3.2）。
 *
 * <p>港湾での荷役作業（受領 / 積込 / 荷降し / 引取 / 税関通過）を 1 件として記録する集約
 * （domain-model.md）。</p>
 *
 * <h2>不変条件</h2>
 * <ul>
 *   <li>LOAD / UNLOAD では航海番号必須</li>
 *   <li>CLAIM では荷受人確認（ClaimVerification）必須</li>
 *   <li>occurredAt は現在時刻より過去または同時（未来時刻は拒否）</li>
 *   <li>同一 trackingNumber + 種別 + 場所 + 近接時刻（5 分以内）の重複登録は拒否</li>
 *   <li>CargoSnapshot ACL の origin / destination と発生場所が異なる場合は警告イベント発行
 *       （記録は許容、{@link UnexpectedHandlingDetectedEvent}）</li>
 * </ul>
 *
 * <p>重複検知と予定外検知は {@link HandlingValidationService} に委譲する
 * （Read Model 経由の集約越境参照）。</p>
 */
@Aggregate
public class HandlingActivity {

    @AggregateIdentifier
    private String activityId;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持
    private String trackingNumber;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持
    private HandlingType handlingType;

    protected HandlingActivity() {
        // Axon required no-arg constructor
    }

    @CommandHandler
    public HandlingActivity(RegisterHandlingActivityCommand command,
                            HandlingValidationService validationService) {
        validate(command);

        // 重複登録は拒否（IT5 3.2）。
        if (validationService.hasDuplicate(command.trackingNumber(), command.handlingType(),
                command.unlocode(), command.occurredAt())) {
            throw new IllegalStateException(
                    "重複した荷役作業の登録です: trackingNumber=" + command.trackingNumber()
                            + " handlingType=" + command.handlingType()
                            + " unlocode=" + command.unlocode()
                            + " occurredAt±5分以内に既存");
        }

        AggregateLifecycle.apply(new HandlingActivityRegisteredEvent(
                command.activityId(),
                command.trackingNumber(),
                command.handlingType(),
                command.occurredAt(),
                command.unlocode(),
                command.voyageNumber(),
                command.handlerId(),
                command.claimVerification()
        ));

        // 予定外検知は警告イベントとして発行（記録自体は許容）。
        Optional<String> unexpectedReason = validationService.detectUnexpected(
                command.trackingNumber(), command.handlingType(), command.unlocode());
        unexpectedReason.ifPresent(reason -> AggregateLifecycle.apply(
                new UnexpectedHandlingDetectedEvent(
                        command.activityId(),
                        command.trackingNumber(),
                        command.handlingType(),
                        command.unlocode(),
                        command.occurredAt(),
                        reason)));
    }

    private void validate(RegisterHandlingActivityCommand command) {
        if (command.occurredAt() == null) {
            throw new IllegalArgumentException("occurredAt は必須です");
        }
        if (command.occurredAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "occurredAt は現在時刻以前である必要があります: " + command.occurredAt());
        }
        boolean isLoadOrUnload = command.handlingType() == HandlingType.LOAD
                || command.handlingType() == HandlingType.UNLOAD;
        if (isLoadOrUnload
                && (command.voyageNumber() == null || command.voyageNumber().isBlank())) {
            throw new IllegalArgumentException(
                    "LOAD / UNLOAD では航海番号が必須です: handlingType=" + command.handlingType());
        }
        if (command.handlingType() == HandlingType.CLAIM
                && command.claimVerification() == null) {
            throw new IllegalArgumentException(
                    "CLAIM では荷受人確認（ClaimVerification）が必須です");
        }
    }

    @EventSourcingHandler
    public void on(HandlingActivityRegisteredEvent event) {
        this.activityId = event.activityId();
        this.trackingNumber = event.trackingNumber();
        this.handlingType = event.handlingType();
    }

    public String getActivityId() {
        return activityId;
    }
}
