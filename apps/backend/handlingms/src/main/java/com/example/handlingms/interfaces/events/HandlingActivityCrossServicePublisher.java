package com.example.handlingms.interfaces.events;

import com.example.handlingms.domain.events.HandlingActivityRegisteredEvent;
import com.example.handlingms.domain.model.ClaimVerification;
import com.example.shared.events.HandlingActivityRegisteredEvent.ClaimVerificationData;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * handling のローカルイベントを shared モジュールのクロスサービスイベントに変換して publish するブリッジ
 * （US15・US16 / IT5 3.3 / ADR-0009）。
 *
 * <p>{@link HandlingActivityRegisteredEvent}（local）を受信し、
 * {@code com.example.shared.events.HandlingActivityRegisteredEvent} に変換して Event Bus に流す。
 * 配信は Axon Kafka Extension の publisher が cargo-events トピックに送り、trackingms が
 * tracking モードで購読する（{@code handling-activity-events} プロセッサ、IT5 3.3）。</p>
 *
 * <p>ローカルとクロスサービスの両イベントは同じドメイン事実を表すが、handlingms 側は
 * ローカル型（{@code HandlingType} enum 等）を保持し、shared 側は文字列で安定契約を提供する。</p>
 */
@Component
@ProcessingGroup("outbound-handling-activity-events")
public class HandlingActivityCrossServicePublisher {

    private static final Logger log = LoggerFactory.getLogger(HandlingActivityCrossServicePublisher.class);

    private final EventGateway eventGateway;

    public HandlingActivityCrossServicePublisher(EventGateway eventGateway) {
        this.eventGateway = eventGateway;
    }

    @EventHandler
    public void on(HandlingActivityRegisteredEvent event) {
        ClaimVerificationData verification = toData(event.claimVerification());
        com.example.shared.events.HandlingActivityRegisteredEvent crossEvent =
                new com.example.shared.events.HandlingActivityRegisteredEvent(
                        event.activityId(),
                        event.trackingNumber(),
                        event.handlingType().name(),
                        event.occurredAt(),
                        event.unlocode(),
                        event.voyageNumber(),
                        event.handlerId(),
                        verification,
                        false
                );
        eventGateway.publish(crossEvent);
        log.debug("[handling-cross-service] published activityId={} trackingNumber={} type={}",
                event.activityId(), event.trackingNumber(), event.handlingType());
    }

    private ClaimVerificationData toData(ClaimVerification v) {
        if (v == null) {
            return null;
        }
        return new ClaimVerificationData(
                v.consigneeName(),
                v.signatureRef(),
                v.confirmationCode(),
                v.verifiedAt()
        );
    }
}
