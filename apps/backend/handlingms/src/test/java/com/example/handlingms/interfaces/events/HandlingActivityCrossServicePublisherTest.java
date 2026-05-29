package com.example.handlingms.interfaces.events;

import com.example.handlingms.domain.events.HandlingActivityRegisteredEvent;
import com.example.handlingms.domain.model.ClaimVerification;
import com.example.handlingms.domain.model.HandlingType;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link HandlingActivityCrossServicePublisher} のユニットテスト（IT5 3.3）。
 */
class HandlingActivityCrossServicePublisherTest {

    private EventGateway eventGateway;
    private HandlingActivityCrossServicePublisher publisher;

    @BeforeEach
    void setUp() {
        eventGateway = mock(EventGateway.class);
        publisher = new HandlingActivityCrossServicePublisher(eventGateway);
    }

    @Test
    @DisplayName("US15: ローカル HandlingActivityRegisteredEvent を shared 形式に変換して publish する")
    void localイベントをshared形式に変換してpublishする() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        publisher.on(new HandlingActivityRegisteredEvent(
                "A-001", "TRK-AB12CD3456", HandlingType.RECEIVE,
                occurredAt, "JPTYO", null, "H-001", null));

        ArgumentCaptor<com.example.shared.events.HandlingActivityRegisteredEvent> captor =
                ArgumentCaptor.forClass(com.example.shared.events.HandlingActivityRegisteredEvent.class);
        verify(eventGateway).publish(captor.capture());
        var e = captor.getValue();
        assertThat(e.activityId()).isEqualTo("A-001");
        assertThat(e.trackingNumber()).isEqualTo("TRK-AB12CD3456");
        assertThat(e.handlingType()).isEqualTo("RECEIVE");
        assertThat(e.unlocode()).isEqualTo("JPTYO");
        assertThat(e.handlerId()).isEqualTo("H-001");
        assertThat(e.claimVerification()).isNull();
    }

    @Test
    @DisplayName("US16: CLAIM の ClaimVerification も shared 形式に変換される")
    void CLAIMの確認情報も変換される() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 16, 14, 0);
        ClaimVerification verification = new ClaimVerification(
                "山田太郎", null, "A1B2C3", occurredAt);

        publisher.on(new HandlingActivityRegisteredEvent(
                "A-002", "TRK-AB12CD3456", HandlingType.CLAIM,
                occurredAt, "USNYC", null, "H-001", verification));

        ArgumentCaptor<com.example.shared.events.HandlingActivityRegisteredEvent> captor =
                ArgumentCaptor.forClass(com.example.shared.events.HandlingActivityRegisteredEvent.class);
        verify(eventGateway).publish(captor.capture());
        var e = captor.getValue();
        assertThat(e.handlingType()).isEqualTo("CLAIM");
        assertThat(e.claimVerification()).isNotNull();
        assertThat(e.claimVerification().consigneeName()).isEqualTo("山田太郎");
        assertThat(e.claimVerification().confirmationCode()).isEqualTo("A1B2C3");
        assertThat(e.claimVerification().signatureRef()).isNull();
    }
}
