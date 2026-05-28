package com.example.trackingms.domain.model;

import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TrackingActivity} 集約の Axon Test Fixture テスト（US14 / IT5 1.3）。
 *
 * <p>Given-When-Then 形式で集約のイベント発行を検証する。
 * 採番は {@code TrackingNumberGenerator} の責務のため、本テストでは正規書式の
 * 追跡番号を直接コマンドに渡す。</p>
 */
class TrackingActivityAggregateTest {

    private FixtureConfiguration<TrackingActivity> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(TrackingActivity.class);
    }

    @Test
    @DisplayName("US14: InitializeTrackingCommand で TrackingInitializedEvent が発行される")
    void 追跡を初期化できる() {
        InitializeTrackingCommand command = new InitializeTrackingCommand(
                "TRK-AB12CD3456", "B-001");

        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new TrackingInitializedEvent("TRK-AB12CD3456", "B-001"));
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
}
