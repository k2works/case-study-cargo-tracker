package com.example.cargotracker.handlingms.domain.model.aggregates;

import com.example.cargotracker.handlingms.domain.model.commands.RegisterHandlingActivityCommand;
import com.example.cargotracker.handlingms.domain.model.events.HandlingActivityRegisteredEvent;
import com.example.cargotracker.handlingms.domain.model.events.UnexpectedHandlingDetectedEvent;
import com.example.cargotracker.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.handlingms.domain.model.valueobjects.VoyageNumber;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * HandlingActivity Aggregate のユニットテスト（US15）。
 *
 * <p>bookingms の {@code CargoTest} と同方針で、{@link EventAppender} をモック化して
 * Command Handler と {@code @EventSourcingHandler} を直接検証する。</p>
 */
@DisplayName("HandlingActivity Aggregate（ユニット）")
class HandlingActivityTest {

    private static final TrackingNumber TRK = new TrackingNumber("TRK-20260720-ABC12345");
    private static final Location TOKYO = Location.of("JPTYO");
    private static final Location HAMBURG = Location.of("DEHAM");
    private static final Location SINGAPORE = Location.of("SGSIN");

    private static CargoSnapshot tokyoToHamburgSnapshot() {
        return new CargoSnapshot(
                "B-2026-0512-001",
                TRK,
                TOKYO,
                HAMBURG,
                "GENERAL");
    }

    private static RegisterHandlingActivityCommand receiveAtTokyo() {
        return new RegisterHandlingActivityCommand(
                UUID.randomUUID().toString(),
                TRK,
                HandlingType.RECEIVE,
                TOKYO,
                LocalDateTime.of(2026, 7, 20, 9, 0),
                null,
                new HandlerId("handler-001"),
                null,
                tokyoToHamburgSnapshot());
    }

    @Test
    @DisplayName("US15: 受領作業を登録すると HandlingActivityRegisteredEvent が発行される")
    void register_受領で登録イベント発行() {
        EventAppender appender = mock(EventAppender.class);
        var command = receiveAtTokyo();
        var snapshot = tokyoToHamburgSnapshot();

        String returned = HandlingActivity.register(command, appender);

        assertThat(returned).isEqualTo(command.activityId());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());

        assertThat(captor.getValue()).isInstanceOf(HandlingActivityRegisteredEvent.class);
        var event = (HandlingActivityRegisteredEvent) captor.getValue();
        assertThat(event.activityId()).isEqualTo(command.activityId());
        assertThat(event.trackingNumber()).isEqualTo(TRK);
        assertThat(event.handlingType()).isEqualTo(HandlingType.RECEIVE);
        assertThat(event.location()).isEqualTo(TOKYO);
        assertThat(event.cargoSnapshot()).isEqualTo(snapshot);
        assertThat(event.unexpected()).isFalse();
    }

    @Test
    @DisplayName("US15 受入7: 予定外場所だと UnexpectedHandlingDetectedEvent も発行される")
    void register_予定外場所で警告イベント追加発行() {
        EventAppender appender = mock(EventAppender.class);
        // 東京発・ハンブルク行きの予約に対してシンガポールで受領を試みる
        var unexpectedReceive = new RegisterHandlingActivityCommand(
                UUID.randomUUID().toString(),
                TRK,
                HandlingType.RECEIVE,
                SINGAPORE,
                LocalDateTime.of(2026, 7, 20, 9, 0),
                null,
                new HandlerId("handler-001"),
                null,
                tokyoToHamburgSnapshot());

        HandlingActivity.register(unexpectedReceive, appender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender, org.mockito.Mockito.times(2)).append(captor.capture());

        var events = captor.getAllValues();
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(HandlingActivityRegisteredEvent.class);
        var registered = (HandlingActivityRegisteredEvent) events.get(0);
        assertThat(registered.unexpected()).isTrue();

        assertThat(events.get(1)).isInstanceOf(UnexpectedHandlingDetectedEvent.class);
        var unexpected = (UnexpectedHandlingDetectedEvent) events.get(1);
        assertThat(unexpected.actualLocation()).isEqualTo(SINGAPORE);
        assertThat(unexpected.expectedOrigin()).isEqualTo(TOKYO);
    }

    @Test
    @DisplayName("US15 受入2/3: LOAD 種別は voyageNumber 必須でイベントに含まれる")
    void register_LOADは航海番号必須() {
        EventAppender appender = mock(EventAppender.class);
        var loadCommand = new RegisterHandlingActivityCommand(
                UUID.randomUUID().toString(),
                TRK,
                HandlingType.LOAD,
                TOKYO,
                LocalDateTime.of(2026, 7, 20, 14, 0),
                new VoyageNumber("V-MOL-001"),
                new HandlerId("handler-001"),
                null,
                tokyoToHamburgSnapshot());

        HandlingActivity.register(loadCommand, appender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        var event = (HandlingActivityRegisteredEvent) captor.getValue();
        assertThat(event.voyageNumber()).isEqualTo(new VoyageNumber("V-MOL-001"));
        assertThat(event.handlingType()).isEqualTo(HandlingType.LOAD);
    }

    @Test
    @DisplayName("HandlingActivityRegisteredEvent を再生すると状態が復元される")
    void on_イベント再生で状態復元() {
        var activity = new HandlingActivity();
        var event = new HandlingActivityRegisteredEvent(
                "ACT-001",
                TRK,
                HandlingType.RECEIVE,
                TOKYO,
                LocalDateTime.of(2026, 7, 20, 9, 0),
                null,
                new HandlerId("handler-001"),
                null,
                tokyoToHamburgSnapshot(),
                false);

        activity.on(event);

        assertThat(activity.getActivityId()).isEqualTo("ACT-001");
        assertThat(activity.getHandlingType()).isEqualTo(HandlingType.RECEIVE);
        assertThat(activity.getLocation()).isEqualTo(TOKYO);
        assertThat(activity.isUnexpected()).isFalse();
    }
}
