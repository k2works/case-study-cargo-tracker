package com.example.cargotracker.tracking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.tracking.domain.model.commands.AdvanceTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.PlanCancellationDischargeCommand;
import com.example.cargotracker.tracking.domain.model.events
        .CancellationDischargePlannedEvent;
import com.example.cargotracker.tracking.domain.model.events.TrackingClosedEvent;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.StatusUpdateSource;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * キャンセルの陸揚げ地（UC22 / US30 / 不変条件 9）。
 *
 * <p><b>承認しても追跡は閉じない。</b> 貨物はまだ船の上にあり、陸揚げの荷役を
 * 記録できなければならない——ここで閉じると、降ろす作業が追跡に残らない。
 * 閉じるのは<b>その港の荷降し（{@code UNLOAD}）を受けてから</b>である。</p>
 *
 * <p><b>{@code TrackingActivityTest} から分けた。</b> 1 ファイルが 500 行を超えると
 * 何を確かめているファイルなのかが読めなくなる（あちらは既に 975 行で、
 * 集約ルートとして抑制の対象になっている）。</p>
 */
class TrackingCancellationTest {

    private static final String NUMBER = "TRK-8K2QX7M4RB";
    private static final Instant NOW = Instant.parse("2026-09-25T02:00:00Z");
    private static final Instant PLANNED_AT = Instant.parse("2026-09-25T01:00:00Z");

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(
                        String.class, TrackingActivity.class))
                .componentRegistry(registry -> registry.registerComponent(
                        Clock.class, c -> Clock.fixed(NOW, ZoneId.of("Asia/Tokyo"))));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static TrackingInitializedEvent initialized() {
        return new TrackingInitializedEvent(NUMBER, "b-1", "SHP-000001", "JPTYO", "USNYC",
                "GENERAL", new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                Instant.parse("2026-09-20T00:00:00Z"),
                                Instant.parse("2026-10-02T00:00:00Z")),
                        new TrackingInitializedEvent.Leg("V-MSK-220", "SGSIN", "USNYC",
                                Instant.parse("2026-10-03T00:00:00Z"),
                                Instant.parse("2026-10-12T00:00:00Z"))),
                Instant.parse("2026-09-10T00:30:00Z"));
    }

    private static PlanCancellationDischargeCommand plan(String unLocode) {
        return new PlanCancellationDischargeCommand(NUMBER, unLocode, "荷主の発注取消",
                "tracker01", PLANNED_AT);
    }

    @Test
    @DisplayName("不変条件 9: 陸揚げ地を記録するが、追跡は閉じない")
    void recordsTheDischargePortWithoutClosing() {
        fixture.given().event(initialized())
                .when().command(plan("SGSIN"))
                // **出るのは 1 本だけ。** 閉じるイベントは出ない——貨物はまだ
                // 船の上にあり、陸揚げの荷役をこれから記録する。
                .then().events(new CancellationDischargePlannedEvent(NUMBER, "b-1",
                        "SGSIN", "荷主の発注取消", "tracker01", PLANNED_AT));
    }

    @Test
    @DisplayName("輸送状態も動かない（降ろす港が変わっただけ）")
    void keepsTheTransportStatus() {
        var activity = new TrackingActivity();
        applyTo(activity, initialized());
        applyTo(activity, new CancellationDischargePlannedEvent(NUMBER, "b-1", "SGSIN",
                "荷主の発注取消", "tracker01", PLANNED_AT));

        assertThat(activity.status())
                .as("貨物は運ばれ続けている。止まったのは「どこへ運ぶか」のほう")
                .isEqualTo(TransportStatus.NOT_RECEIVED);
    }

    @Test
    @DisplayName("二度届いても 1 度だけ（Event Processor は at-least-once）")
    void isIdempotent() {
        fixture.given().event(initialized())
                .event(new CancellationDischargePlannedEvent(NUMBER, "b-1", "SGSIN",
                        "荷主の発注取消", "tracker01", PLANNED_AT))
                .when().command(plan("SGSIN"))
                .then().noEvents();
    }

    @Test
    @DisplayName("始まっていない追跡には記録できない")
    void refusesBeforeTheTrackingStarts() {
        fixture.given().noPriorActivity()
                .when().command(plan("SGSIN"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("不変条件 9: 指定した港で荷降しすると追跡が閉じる")
    void closesAfterTheDischarge() {
        fixture.given().event(initialized())
                .event(planned("SGSIN"))
                .event(received())
                .event(loaded())
                .when().command(unload("SGSIN", "act-3"))
                .then().eventsSatisfy(events -> assertThat(events)
                        .as("キャンセルの陸揚げが済んだので閉じる")
                        .map(event -> event.payload().getClass().getSimpleName())
                        .contains("TrackingClosedEvent"));
    }

    @Test
    @DisplayName("不変条件 9: 途中の港で降ろしても閉じない（積み替えがある）")
    void doesNotCloseAtAnotherPort() {
        // **閉じてしまうと、そのあとの荷役が記録できない。**
        fixture.given().event(initialized())
                .event(planned("USNYC"))
                .event(received())
                .event(loaded())
                .when().command(unload("SGSIN", "act-3"))
                .then().eventsSatisfy(events -> assertThat(events)
                        .map(event -> event.payload().getClass().getSimpleName())
                        .doesNotContain("TrackingClosedEvent"));
    }

    @Test
    @DisplayName("キャンセルされていない追跡は、荷降ししても閉じない")
    void doesNotCloseWithoutACancellation() {
        fixture.given().event(initialized())
                .event(received())
                .event(loaded())
                .when().command(unload("SGSIN", "act-3"))
                .then().eventsSatisfy(events -> assertThat(events)
                        .map(event -> event.payload().getClass().getSimpleName())
                        .doesNotContain("TrackingClosedEvent"));
    }

    @Test
    @DisplayName("一度閉じたら二度閉じない（荷役は再配送されうる）")
    void closesOnlyOnce() {
        fixture.given().event(initialized())
                .event(planned("SGSIN"))
                .event(received())
                .event(loaded())
                .event(new TrackingClosedEvent(NUMBER, "b-1", "CANCELLED", "SGSIN", NOW))
                .when().command(unload("SGSIN", "act-9"))
                .then().eventsSatisfy(events -> assertThat(events)
                        .map(event -> event.payload().getClass().getSimpleName())
                        .doesNotContain("TrackingClosedEvent"));
    }

    private static CancellationDischargePlannedEvent planned(String unLocode) {
        return new CancellationDischargePlannedEvent(NUMBER, "b-1", unLocode,
                "荷主の発注取消", "tracker01", PLANNED_AT);
    }

    /** 東京で受領 → 積込。ここまで来て初めて荷降しが通る（遷移表）。 */
    private static TransportStatusUpdatedEvent received() {
        return new TransportStatusUpdatedEvent(NUMBER, TransportStatus.NOT_RECEIVED,
                TransportStatus.RECEIVED, StatusUpdateSource.HANDLING, "act-1", "JPTYO",
                Instant.parse("2026-09-20T01:00:00Z"), "handler01", NOW);
    }

    private static TransportStatusUpdatedEvent loaded() {
        return new TransportStatusUpdatedEvent(NUMBER, TransportStatus.RECEIVED,
                TransportStatus.LOADED, StatusUpdateSource.HANDLING, "act-2",
                "JPTYO", Instant.parse("2026-09-20T02:00:00Z"), "handler01", NOW);
    }

    private static AdvanceTrackingCommand unload(String unLocode, String activityId) {
        return new AdvanceTrackingCommand(NUMBER, activityId, "UNLOAD", unLocode,
                false, false, "handler01", Instant.parse("2026-10-02T01:00:00Z"));
    }

    /**
     * イベントを集約へ当てる。
     *
     * <p><b>本番と同じ復元経路を通す。</b> フィールドを直接組み立てると、
     * {@code @EventSourcingHandler} の書き漏らしを素通りさせる。</p>
     */
    private static void applyTo(TrackingActivity activity, Object event) {
        for (var method : TrackingActivity.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(org.axonframework.eventsourcing.annotation
                    .EventSourcingHandler.class)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(event)) {
                method.setAccessible(true);
                try {
                    method.invoke(activity, event);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                return;
            }
        }
        throw new IllegalStateException("復元ハンドラがありません: " + event.getClass());
    }
}
