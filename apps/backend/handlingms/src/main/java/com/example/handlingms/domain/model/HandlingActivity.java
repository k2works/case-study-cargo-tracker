package com.example.handlingms.domain.model;

import com.example.handlingms.domain.commands.RegisterHandlingActivityCommand;
import com.example.handlingms.domain.events.HandlingActivityRegisteredEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

/**
 * 荷役活動集約（{@code HandlingActivity}、US15・US16 / IT5 タスク 3.2）。
 *
 * <p>港湾での荷役作業（受領 / 積込 / 荷降し / 引取 / 税関通過）を 1 件として記録する集約
 * （domain-model.md）。本コミットでは集約の生成（{@link RegisterHandlingActivityCommand}）と
 * 不変条件（LOAD/UNLOAD で航海番号必須、CLAIM で荷受人確認必須）のみを実装する。</p>
 *
 * <p>CargoSnapshot ACL（IT5 タスク 3.1）は本集約のフィールドとして展開予定だが、ACL 経由の
 * 「予定外場所検知」「重複登録検知」「occurredAt 過去または同時」などの追加不変条件は後続
 * イテレーションで段階追加する。</p>
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
    public HandlingActivity(RegisterHandlingActivityCommand command) {
        validate(command);
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
    }

    private void validate(RegisterHandlingActivityCommand command) {
        if (command.handlingType() == HandlingType.LOAD
                || command.handlingType() == HandlingType.UNLOAD) {
            if (command.voyageNumber() == null || command.voyageNumber().isBlank()) {
                throw new IllegalArgumentException(
                        "LOAD / UNLOAD では航海番号が必須です: handlingType=" + command.handlingType());
            }
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
