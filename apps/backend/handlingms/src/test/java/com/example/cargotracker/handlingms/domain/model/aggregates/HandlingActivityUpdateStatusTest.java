package com.example.cargotracker.handlingms.domain.model.aggregates;

import com.example.cargotracker.handlingms.domain.model.commands.UpdateCargoStatusCommand;
import com.example.cargotracker.handlingms.domain.model.events.CargoStatusUpdatedEvent;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * HandlingActivity#updateStatus（US17）のユニットテスト。
 */
@DisplayName("HandlingActivity.updateStatus（US17）")
class HandlingActivityUpdateStatusTest {

    private static final TrackingNumber TRK = new TrackingNumber("TRK-20260725-STATUS01");

    @Test
    @DisplayName("US17 受入2/3: updateStatus は CargoStatusUpdatedEvent を発行する")
    void updateStatusでイベント発行() {
        EventAppender appender = mock(EventAppender.class);
        var command = new UpdateCargoStatusCommand(
                UUID.randomUUID().toString(),
                TRK,
                "IN_TRANSIT",
                Location.of("SGSIN"),
                LocalDateTime.of(2026, 7, 25, 8, 0),
                new HandlerId("tracker-001"));

        String returned = HandlingActivity.updateStatus(command, appender);
        assertThat(returned).isEqualTo(command.activityId());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(CargoStatusUpdatedEvent.class);
        var event = (CargoStatusUpdatedEvent) captor.getValue();
        assertThat(event.trackingNumber()).isEqualTo(TRK);
        assertThat(event.newStatus()).isEqualTo("IN_TRANSIT");
        assertThat(event.location().unLocode().value()).isEqualTo("SGSIN");
        assertThat(event.operatorId().value()).isEqualTo("tracker-001");
    }

    @Test
    @DisplayName("US17: 許可されていない状態への遷移は Command 生成時に拒否される")
    void 不正状態遷移は拒否() {
        assertThatThrownBy(() -> new UpdateCargoStatusCommand(
                UUID.randomUUID().toString(),
                TRK,
                "INVALID_STATUS",
                Location.of("SGSIN"),
                LocalDateTime.now(),
                new HandlerId("tracker-001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("許可されていない状態への遷移です: INVALID_STATUS");
    }
}
