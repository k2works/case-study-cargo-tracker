package com.example.trackingms.domain.model;

import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.domain.commands.RegisterTrackingExceptionCommand;
import com.example.trackingms.domain.commands.ResolveTrackingExceptionCommand;
import com.example.trackingms.domain.commands.UpdateTransportStatusCommand;
import com.example.shared.events.CargoDeliveredEvent;
import com.example.trackingms.domain.events.CargoMisroutedEvent;
import com.example.trackingms.domain.events.TrackingExceptionEscalatedEvent;
import com.example.trackingms.domain.events.TrackingExceptionRegisteredEvent;
import com.example.trackingms.domain.events.TrackingExceptionResolvedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import com.example.trackingms.domain.services.TransportStatusTransition;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * {@link TrackingActivity} 集約の Axon Test Fixture テスト（US14 / IT5 1.3）。
 *
 * <p>Given-When-Then 形式で集約のイベント発行を検証する。
 * 採番は {@code TrackingNumberGenerator} の責務のため、本テストでは正規書式の
 * 追跡番号を直接コマンドに渡す。</p>
 */
class TrackingActivityAggregateTest {

    private FixtureConfiguration<TrackingActivity> fixture;

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(TrackingActivity.class);
        // US17 タスク 2.2：状態遷移ガード（ドメインサービス）を Aggregate のリソースとして注入
        fixture.registerInjectableResource(new TransportStatusTransition());
        // US19 / US20 タスク 2.2：例外解決日時を固定するための Clock
        fixture.registerInjectableResource(
                Clock.fixed(FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("US14: InitializeTrackingCommand で TrackingInitializedEvent + shared CargoTrackedEvent が連続発行（IT8 T1.10 集約発火型）")
    void 追跡を初期化できる() {
        InitializeTrackingCommand command = new InitializeTrackingCommand(
                "TRK-AB12CD3456", "B-001");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"),
                        new com.example.shared.events.CargoTrackedEvent("B-001", "TRK-AB12CD3456"));
    }

    @Test
    @DisplayName("US14: 追跡番号は TrackingNumber の書式に準拠する必要がある")
    void 不正な書式の追跡番号は拒否する() {
        InitializeTrackingCommand command = new InitializeTrackingCommand(
                "BAD-FORMAT", "B-001");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US14: bookingId は必須")
    void bookingIdが空ならば拒否する() {
        InitializeTrackingCommand command = new InitializeTrackingCommand(
                "TRK-AB12CD3456", "");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    // --- IT5 タスク 2.2：US17 貨物状態手動更新 ---

    @Test
    @DisplayName("US17: 許可遷移（NOT_RECEIVED→RECEIVED）で TransportStatusUpdatedEvent が発行される")
    void 許可遷移で状態が更新される() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        UpdateTransportStatusCommand command = new UpdateTransportStatusCommand(
                "TRK-AB12CD3456", TrackingActivityAggregateTest.TransportStatusFixture.RECEIVED,
                "JPTYO", null, occurredAt, "貨物を東京港で受領");

        fixture.given(new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new TransportStatusUpdatedEvent(
                        "TRK-AB12CD3456",
                        TrackingActivityAggregateTest.TransportStatusFixture.NOT_RECEIVED,
                        TrackingActivityAggregateTest.TransportStatusFixture.RECEIVED,
                        "JPTYO", null, occurredAt, "貨物を東京港で受領"));
    }

    @Test
    @DisplayName("US17: 不正遷移（NOT_RECEIVED→DELIVERED）は IllegalStateException")
    void 不正遷移は拒否される() {
        UpdateTransportStatusCommand command = new UpdateTransportStatusCommand(
                "TRK-AB12CD3456", TrackingActivityAggregateTest.TransportStatusFixture.DELIVERED,
                "USNYC", null, LocalDateTime.of(2026, 8, 1, 10, 0), "誤った遷移");

        fixture.given(new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"))
                .when(command)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US17: MISROUTED への遷移で TransportStatusUpdatedEvent + CargoMisroutedEvent の 2 件が発行される")
    void 誤配送検知でCargoMisroutedEventが発行される() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 22, 12, 0);
        UpdateTransportStatusCommand command = new UpdateTransportStatusCommand(
                "TRK-AB12CD3456", TrackingActivityAggregateTest.TransportStatusFixture.MISROUTED,
                "CNHKG", null, occurredAt, "想定外の港で発見");

        fixture.given(
                        new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.NOT_RECEIVED,
                                TrackingActivityAggregateTest.TransportStatusFixture.RECEIVED,
                                "JPTYO", null,
                                LocalDateTime.of(2026, 7, 20, 10, 0), null))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.RECEIVED,
                                TrackingActivityAggregateTest.TransportStatusFixture.MISROUTED,
                                "CNHKG", null, occurredAt, "想定外の港で発見"),
                        new CargoMisroutedEvent("TRK-AB12CD3456", "CNHKG", occurredAt));
    }

    @Test
    @DisplayName("US16/T2: AWAITING_CLAIM → DELIVERED 遷移で CargoDeliveredEvent も発行される（集約発火型、ADR-0012）")
    void DELIVERED遷移でCargoDeliveredEventが発行される() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 16, 14, 0);
        UpdateTransportStatusCommand command = new UpdateTransportStatusCommand(
                "TRK-AB12CD3456",
                TrackingActivityAggregateTest.TransportStatusFixture.DELIVERED,
                "USNYC", null, occurredAt, "荷受人引取");

        fixture.given(
                        new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.NOT_RECEIVED,
                                TrackingActivityAggregateTest.TransportStatusFixture.RECEIVED,
                                "JPTYO", null,
                                LocalDateTime.of(2026, 7, 20, 10, 0), null),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.RECEIVED,
                                TrackingActivityAggregateTest.TransportStatusFixture.LOADED,
                                "JPTYO", "V-MAERSK-220",
                                LocalDateTime.of(2026, 7, 21, 9, 0), null),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.LOADED,
                                TrackingActivityAggregateTest.TransportStatusFixture.IN_TRANSIT,
                                "JPTYO", "V-MAERSK-220",
                                LocalDateTime.of(2026, 7, 21, 18, 0), null),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.IN_TRANSIT,
                                TrackingActivityAggregateTest.TransportStatusFixture.UNLOADED,
                                "USNYC", "V-MAERSK-220",
                                LocalDateTime.of(2026, 8, 15, 9, 0), null),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.UNLOADED,
                                TrackingActivityAggregateTest.TransportStatusFixture.AWAITING_CLAIM,
                                "USNYC", null,
                                LocalDateTime.of(2026, 8, 15, 10, 0), null))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.AWAITING_CLAIM,
                                TrackingActivityAggregateTest.TransportStatusFixture.DELIVERED,
                                "USNYC", null, occurredAt, "荷受人引取"),
                        new CargoDeliveredEvent("TRK-AB12CD3456", "B-001", occurredAt));
    }

    @Test
    @DisplayName("US17: 終端 DELIVERED からの再遷移は拒否される")
    void DELIVERED後の遷移は拒否される() {
        UpdateTransportStatusCommand command = new UpdateTransportStatusCommand(
                "TRK-AB12CD3456", TrackingActivityAggregateTest.TransportStatusFixture.EXCEPTION,
                "USNYC", null, LocalDateTime.of(2026, 9, 1, 0, 0), null);

        fixture.given(
                        new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.NOT_RECEIVED,
                                TrackingActivityAggregateTest.TransportStatusFixture.RECEIVED,
                                "JPTYO", null, LocalDateTime.of(2026, 7, 20, 10, 0), null),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.RECEIVED,
                                TrackingActivityAggregateTest.TransportStatusFixture.LOADED,
                                "JPTYO", "V-MAERSK-220",
                                LocalDateTime.of(2026, 7, 21, 9, 0), null),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.LOADED,
                                TrackingActivityAggregateTest.TransportStatusFixture.IN_TRANSIT,
                                "JPTYO", "V-MAERSK-220",
                                LocalDateTime.of(2026, 7, 21, 18, 0), null),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.IN_TRANSIT,
                                TrackingActivityAggregateTest.TransportStatusFixture.UNLOADED,
                                "USNYC", "V-MAERSK-220",
                                LocalDateTime.of(2026, 8, 15, 9, 0), null),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.UNLOADED,
                                TrackingActivityAggregateTest.TransportStatusFixture.AWAITING_CLAIM,
                                "USNYC", null,
                                LocalDateTime.of(2026, 8, 15, 10, 0), null),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TrackingActivityAggregateTest.TransportStatusFixture.AWAITING_CLAIM,
                                TrackingActivityAggregateTest.TransportStatusFixture.DELIVERED,
                                "USNYC", null,
                                LocalDateTime.of(2026, 8, 16, 14, 0), null))
                .when(command)
                .expectException(IllegalStateException.class);
    }

    // --- IT6 タスク 2.2 + 3.1：US19 / US20 例外管理 ---

    @Test
    @DisplayName("US19: RegisterTrackingExceptionCommand(DELAY) で 状態 → EXCEPTION + 例外登録の 2 件が発行される")
    void DELAY例外を登録できる() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 25, 9, 0);
        RegisterTrackingExceptionCommand command = new RegisterTrackingExceptionCommand(
                "TRK-AB12CD3456", "EX-001",
                com.example.trackingms.domain.model.ExceptionType.DELAY,
                occurredAt, "SGSIN", "悪天候のため寄港不可");

        fixture.given(
                        new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TransportStatusFixture.NOT_RECEIVED, TransportStatusFixture.RECEIVED,
                                "JPTYO", null, LocalDateTime.of(2026, 7, 20, 10, 0), null))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TransportStatusFixture.RECEIVED, TransportStatusFixture.EXCEPTION,
                                "SGSIN", null, occurredAt, "例外発生: DELAY"),
                        new TrackingExceptionRegisteredEvent(
                                "TRK-AB12CD3456", "EX-001",
                                com.example.trackingms.domain.model.ExceptionType.DELAY,
                                occurredAt, "SGSIN", "悪天候のため寄港不可", false));
    }

    @Test
    @DisplayName("US20: LOSS 種別では TrackingExceptionEscalatedEvent も追加発行される（3 件）")
    void LOSS例外でescalation発火() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 26, 14, 0);
        RegisterTrackingExceptionCommand command = new RegisterTrackingExceptionCommand(
                "TRK-AB12CD3456", "EX-002",
                com.example.trackingms.domain.model.ExceptionType.LOSS,
                occurredAt, "CNSHA", "貨物紛失を確認");

        fixture.given(
                        new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TransportStatusFixture.NOT_RECEIVED, TransportStatusFixture.IN_TRANSIT,
                                "CNSHA", null, LocalDateTime.of(2026, 7, 24, 10, 0), null))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TransportStatusFixture.IN_TRANSIT, TransportStatusFixture.EXCEPTION,
                                "CNSHA", null, occurredAt, "例外発生: LOSS"),
                        new TrackingExceptionRegisteredEvent(
                                "TRK-AB12CD3456", "EX-002",
                                com.example.trackingms.domain.model.ExceptionType.LOSS,
                                occurredAt, "CNSHA", "貨物紛失を確認", true),
                        new TrackingExceptionEscalatedEvent(
                                "TRK-AB12CD3456", "EX-002",
                                com.example.trackingms.domain.model.ExceptionType.LOSS, occurredAt));
    }

    @Test
    @DisplayName("US20: DAMAGE は escalated = false（escalation 発行なし、2 件のみ）")
    void DAMAGE例外でescalationなし() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 27, 10, 0);
        RegisterTrackingExceptionCommand command = new RegisterTrackingExceptionCommand(
                "TRK-AB12CD3456", "EX-003",
                com.example.trackingms.domain.model.ExceptionType.DAMAGE,
                occurredAt, "JPOSA", "外装に損傷あり");

        fixture.given(new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TransportStatusFixture.NOT_RECEIVED, TransportStatusFixture.EXCEPTION,
                                "JPOSA", null, occurredAt, "例外発生: DAMAGE"),
                        new TrackingExceptionRegisteredEvent(
                                "TRK-AB12CD3456", "EX-003",
                                com.example.trackingms.domain.model.ExceptionType.DAMAGE,
                                occurredAt, "JPOSA", "外装に損傷あり", false));
    }

    @Test
    @DisplayName("US19/US20: EXCEPTION 状態の貨物には新規例外を登録できない（IllegalStateException）")
    void EXCEPTION状態では追加登録不可() {
        RegisterTrackingExceptionCommand command = new RegisterTrackingExceptionCommand(
                "TRK-AB12CD3456", "EX-004",
                com.example.trackingms.domain.model.ExceptionType.DELAY,
                LocalDateTime.now(), "SGSIN", "追加遅延");

        fixture.given(
                        new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"),
                        // 既に EXCEPTION に遷移済み
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TransportStatusFixture.NOT_RECEIVED, TransportStatusFixture.EXCEPTION,
                                "JPTYO", null, LocalDateTime.of(2026, 7, 20, 10, 0), "例外発生"))
                .when(command)
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US19: ResolveTrackingExceptionCommand で TrackingExceptionResolvedEvent が発行される")
    void 例外を解決できる() {
        ResolveTrackingExceptionCommand command = new ResolveTrackingExceptionCommand(
                "TRK-AB12CD3456", "EX-001",
                "代替ルート手配済み。新到着予定 2026-08-20");

        fixture.given(
                        new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TransportStatusFixture.NOT_RECEIVED, TransportStatusFixture.EXCEPTION,
                                "SGSIN", null, LocalDateTime.of(2026, 7, 25, 9, 0), "例外発生: DELAY"),
                        new TrackingExceptionRegisteredEvent(
                                "TRK-AB12CD3456", "EX-001",
                                com.example.trackingms.domain.model.ExceptionType.DELAY,
                                LocalDateTime.of(2026, 7, 25, 9, 0), "SGSIN", "悪天候", false))
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new TrackingExceptionResolvedEvent(
                        "TRK-AB12CD3456", "EX-001",
                        "代替ルート手配済み。新到着予定 2026-08-20",
                        FIXED_NOW));
    }

    @Test
    @DisplayName("US19: 存在しない exceptionId で resolve すると IllegalArgumentException")
    void 存在しない例外をresolveできない() {
        ResolveTrackingExceptionCommand command = new ResolveTrackingExceptionCommand(
                "TRK-AB12CD3456", "EX-NOPE", "何かしらの対応");

        fixture.given(new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"))
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US19: resolution が空で resolve すると IllegalArgumentException")
    void resolutionが空ならresolveできない() {
        ResolveTrackingExceptionCommand command = new ResolveTrackingExceptionCommand(
                "TRK-AB12CD3456", "EX-001", "");

        fixture.given(
                        new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TransportStatusFixture.NOT_RECEIVED, TransportStatusFixture.EXCEPTION,
                                "SGSIN", null, LocalDateTime.of(2026, 7, 25, 9, 0), "例外発生"),
                        new TrackingExceptionRegisteredEvent(
                                "TRK-AB12CD3456", "EX-001",
                                com.example.trackingms.domain.model.ExceptionType.DELAY,
                                LocalDateTime.of(2026, 7, 25, 9, 0), "SGSIN", "悪天候", false))
                .when(command)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US19: 同一 exceptionId を 2 回登録しようとすると IllegalArgumentException")
    void 同一例外IDの重複登録は拒否() {
        RegisterTrackingExceptionCommand command = new RegisterTrackingExceptionCommand(
                "TRK-AB12CD3456", "EX-001",
                com.example.trackingms.domain.model.ExceptionType.DELAY,
                LocalDateTime.now(), "SGSIN", "重複");

        fixture.given(
                        new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"),
                        new TransportStatusUpdatedEvent("TRK-AB12CD3456",
                                TransportStatusFixture.NOT_RECEIVED, TransportStatusFixture.EXCEPTION,
                                "JPTYO", null, LocalDateTime.of(2026, 7, 20, 10, 0), "例外発生"),
                        new TrackingExceptionRegisteredEvent(
                                "TRK-AB12CD3456", "EX-001",
                                com.example.trackingms.domain.model.ExceptionType.DELAY,
                                LocalDateTime.of(2026, 7, 25, 9, 0), "SGSIN", "悪天候", false))
                .when(command)
                .expectException(IllegalStateException.class); // 状態 EXCEPTION でも拒否
    }

    /** テスト可読性のための短縮エイリアス。 */
    static final class TransportStatusFixture {
        static final TransportStatus NOT_RECEIVED = TransportStatus.NOT_RECEIVED;
        static final TransportStatus RECEIVED = TransportStatus.RECEIVED;
        static final TransportStatus LOADED = TransportStatus.LOADED;
        static final TransportStatus IN_TRANSIT = TransportStatus.IN_TRANSIT;
        static final TransportStatus UNLOADED = TransportStatus.UNLOADED;
        static final TransportStatus AWAITING_CLAIM = TransportStatus.AWAITING_CLAIM;
        static final TransportStatus DELIVERED = TransportStatus.DELIVERED;
        static final TransportStatus MISROUTED = TransportStatus.MISROUTED;
        static final TransportStatus EXCEPTION = TransportStatus.EXCEPTION;
    }
}
