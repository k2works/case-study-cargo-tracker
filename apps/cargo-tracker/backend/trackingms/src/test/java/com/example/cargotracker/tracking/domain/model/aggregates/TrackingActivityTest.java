package com.example.cargotracker.tracking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.tracking.domain.model.commands.AdvanceTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.RevertTrackingCommand;
import com.example.cargotracker.tracking.domain.model.commands.UpdateTransportStatusCommand;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusRevertedEvent;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.StatusUpdateSource;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 追跡の開始（UC12 / US14）。<b>trackingms の最初の集約</b>。
 *
 * <p>bookingms から契約コマンドで届く。<b>状態は載って来ない</b>——追跡を始めた
 * 直後がどの状態かは trackingms が決める（{@code NOT_RECEIVED}）。送る側が相手の
 * 状態機械を知っていることにしない。</p>
 */
class TrackingActivityTest {

    private static final Instant NOW = Instant.parse("2026-09-08T01:00:00Z");
    private static final Instant ISSUED = Instant.parse("2026-09-08T00:30:00Z");

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

    private static InitializeTrackingCommand initialize() {
        return new InitializeTrackingCommand("TRK-8K2QX7M4RB", "b-1", "SHP-000001", "JPTYO",
                "USNYC", "GENERAL",
                List.of(new InitializeTrackingCommand.LegDto("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                ISSUED);
    }

    @Test
    @DisplayName("US14 §3: 追跡を開始すると貨物状態が未受領になる")
    void initializesTracking() {
        // **開始した時刻は集約の Clock で決める。** 発行時刻（issuedAt）をそのまま
        // 使うと、連鎖が数時間止まっていた場合に「止まっていなかった」ように見える。
        fixture.given().noPriorActivity()
                .when().command(initialize())
                .then().success()
                .events(new TrackingInitializedEvent("TRK-8K2QX7M4RB", "b-1", "SHP-000001", "JPTYO", "USNYC",
                        "GENERAL",
                        List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                                Instant.parse("2026-09-10T09:00:00Z"),
                                Instant.parse("2026-09-24T18:00:00Z"))),
                        NOW));
    }

    @Test
    @DisplayName("US14: 二重に開始しない（連鎖が再送しても追跡は 1 つ）")
    void rejectsSecondInitialization() {
        // 連鎖は失敗したら再試行する。同じコマンドが 2 度届いたときに追跡が
        // 2 つできると、荷役がどちらに付くのか決まらない。
        fixture.given().events(new TrackingInitializedEvent("TRK-8K2QX7M4RB", "b-1", "SHP-000001", "JPTYO",
                        "USNYC", "GENERAL", List.of(), NOW))
                .when().command(initialize())
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US14: 旅程の無い追跡は始めない（経路が決まってから発行される）")
    void rejectsEmptyItinerary() {
        fixture.given().noPriorActivity()
                .when().command(new InitializeTrackingCommand("TRK-8K2QX7M4RB", "b-1", "SHP-000001",
                        "JPTYO", "USNYC", "GENERAL", List.of(), ISSUED))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US14: 予約の分からない追跡は始めない（誰の荷物か辿れなくなる）")
    void rejectsMissingBookingId() {
        fixture.given().noPriorActivity()
                .when().command(new InitializeTrackingCommand("TRK-8K2QX7M4RB", "  ", "SHP-000001",
                        "JPTYO", "USNYC", "GENERAL", initialize().legs(), ISSUED))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US14: 予約 ID が無い（null）追跡も始めない")
    void rejectsNullBookingId() {
        // 空文字だけを試すと、null の分岐が残る。
        fixture.given().noPriorActivity()
                .when().command(new InitializeTrackingCommand("TRK-8K2QX7M4RB", null, "SHP-000001",
                        "JPTYO", "USNYC", "GENERAL", initialize().legs(), ISSUED))
                .then().exception(BusinessRuleViolation.class);
    }

    @ParameterizedTest
    @EnumSource(TransportStatus.class)
    @DisplayName("輸送状態は列挙名でない日本語の呼び名を持つ")
    void transportStatusHasJapaneseLabel(TransportStatus status) {
        assertThat(status.label()).isNotBlank().isNotEqualTo(status.name());
        assertThat(status.label()).doesNotMatch("^[A-Z_]+$");
    }

    @ParameterizedTest
    @EnumSource(TransportStatus.class)
    @DisplayName("輸送状態の呼び名が設計の要素表と一致する（IT7 H.2）")
    void transportStatusLabelMatchesTheCanon(TransportStatus status) throws Exception {
        // **利用者に見せる文字列は、突き合わせないと黙ってずれる**（IT6 で
        // RoutingStatus の呼び名が実装だけ違っていた）。要素表が正典。
        // 実行時のカレントは trackingms。docs は backend の 3 つ上にある。
        String canon = java.nio.file.Files.readString(java.nio.file.Path.of(
                "../../../../docs/design/cargo-tracker/domain-model.md"));
        // **TransportStatus の節に絞る。** `DELIVERED` は BookingStatus にもあり、
        // 表全体から探すと別の行の呼び名（予約の「引取済」）と突き合わせてしまう。
        java.util.List<String> lines = canon.lines().toList();
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("輸送ステータス `TransportStatus`")) {
                start = i;
                break;
            }
        }
        assertThat(start).as("要素表に TransportStatus の節が無い").isNotNegative();
        String row = null;
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            // 次の種別の行に入ったら打ち切る（`| |` で続くあいだが同じ節）。
            if (i > start && !line.startsWith("| |")) {
                break;
            }
            if (line.contains("`" + status.name() + "`")) {
                row = line;
                break;
            }
        }
        assertThat(row).as("%s が TransportStatus の要素表に無い", status).isNotNull();
        assertThat(row)
                .as("%s の呼び名が設計と食い違う", status)
                .contains(status.label());
    }

    // ---- US17 状態の手動更新（T3） ----

    private static final String NUMBER = "TRK-8K2QX7M4RB";

    private static UpdateTransportStatusCommand update(TransportStatus to) {
        return new UpdateTransportStatusCommand(NUMBER, to, "JPTYO",
                Instant.parse("2026-09-11T02:00:00Z"), "tracker-1");
    }

    private static TrackingInitializedEvent initialized() {
        return new TrackingInitializedEvent(NUMBER, "b-1", "SHP-000001", "JPTYO", "USNYC", "GENERAL",
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                ISSUED);
    }

    @Test
    @DisplayName("US17 §2: 新しい状態・位置・日時を入力して更新できる")
    void updatesStatusManually() {
        fixture.given().event(initialized())
                .when().command(update(TransportStatus.RECEIVED))
                .then().events(new TransportStatusUpdatedEvent(NUMBER,
                        TransportStatus.NOT_RECEIVED, TransportStatus.RECEIVED,
                        StatusUpdateSource.MANUAL, null, "JPTYO",
                        Instant.parse("2026-09-11T02:00:00Z"), "tracker-1", NOW));
    }

    @Test
    @DisplayName("不変条件 2: 正典が許さない遷移は断る")
    void rejectsTransitionsTheCanonForbids() {
        // 未受領からいきなり引取済にはしない。**集約が判定を書き直さず、
        // TransportStatus#canTransitionTo をそのまま呼ぶ**（判定が 2 つあると片方だけ直る）。
        fixture.given().event(initialized())
                .when().command(update(TransportStatus.DELIVERED))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("同じ状態への更新は断る（履歴に同じ行が積み上がるだけ）")
    void rejectsUpdateToTheSameStatus() {
        fixture.given().event(initialized())
                .when().command(update(TransportStatus.NOT_RECEIVED))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("不変条件 5 の下地: 例外発生中は手動で動かさない")
    void doesNotMoveWhileAnExceptionIsOpen() {
        // **例外の解決は「例外前の状態へ戻る」**（不変条件 5）。解決を待たずに手で
        // 動かすと、戻り先（statusBeforeException）と実際の状態が食い違う。
        fixture.given().events(initialized(),
                new TransportStatusUpdatedEvent(NUMBER, TransportStatus.NOT_RECEIVED,
                        TransportStatus.RECEIVED, StatusUpdateSource.MANUAL, null, "JPTYO",
                        Instant.parse("2026-09-11T02:00:00Z"), "tracker-1", NOW),
                new TransportStatusUpdatedEvent(NUMBER, TransportStatus.RECEIVED,
                        TransportStatus.EXCEPTION, StatusUpdateSource.MANUAL, null, "JPTYO",
                        Instant.parse("2026-09-11T03:00:00Z"), "tracker-1", NOW))
                .when().command(update(TransportStatus.LOADED))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("始まっていない追跡は更新できない")
    void rejectsUpdateBeforeInitialization() {
        fixture.given().noPriorActivity()
                .when().command(update(TransportStatus.RECEIVED))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("更新した人が分からない記録は残さない")
    void requiresWhoUpdated() {
        fixture.given().event(initialized())
                .when().command(new UpdateTransportStatusCommand(NUMBER, TransportStatus.RECEIVED,
                        "JPTYO", Instant.parse("2026-09-11T02:00:00Z"), "  "))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("誤配・例外発生は手では入れられない（起きていない誤配を記録できてしまう）")
    void rejectsStatusesThatAreNotSetByHand() {
        // **例外発生は行き止まりになる。** 例外中は手で動かせず、解決の画面は
        // まだ無いので、選んだ人は自分で戻せない。
        fixture.given().event(initialized())
                .when().command(update(TransportStatus.MISROUTED))
                .then().exception(BusinessRuleViolation.class);
    }

    // ---- US15 荷役から貨物状態が進む（IT9 T6） ----

    private static final Instant HANDLED = Instant.parse("2026-09-10T02:00:00Z");

    private static AdvanceTrackingCommand advance(String handlingType, boolean finalPort,
            boolean offRoute) {
        return advance("act-1", handlingType, finalPort, offRoute);
    }

    private static AdvanceTrackingCommand advance(String activityId, String handlingType,
            boolean finalPort, boolean offRoute) {
        return new AdvanceTrackingCommand(NUMBER, activityId, handlingType, "JPTYO", finalPort,
                offRoute, "handler01", HANDLED);
    }

    @Test
    @DisplayName("US15 §4: 荷役を受けると貨物状態が進む")
    void advancesOnHandling() {
        fixture.given().event(initialized())
                .when().command(advance("RECEIVE", false, false))
                .then().events(new TransportStatusUpdatedEvent(NUMBER,
                        TransportStatus.NOT_RECEIVED, TransportStatus.RECEIVED,
                        StatusUpdateSource.HANDLING, "act-1", "JPTYO", HANDLED, "handler01", NOW));
    }

    @Test
    @DisplayName("US28 の下地: 予定外の荷役は誤配にする")
    void marksMisroutedOnOffRouteHandling() {
        fixture.given().event(initialized())
                .when().command(advance("RECEIVE", false, true))
                .then().events(new TransportStatusUpdatedEvent(NUMBER,
                        TransportStatus.NOT_RECEIVED, TransportStatus.MISROUTED,
                        StatusUpdateSource.HANDLING, "act-1", "JPTYO", HANDLED, "handler01", NOW));
    }

    @Test
    @DisplayName("不変条件 8: 知らない追跡番号の荷役では止まらない")
    void doesNotFailForUnknownTracking() {
        // **例外にすると Event Processor が止まり、後続の荷役まで届かなくなる。**
        // 荷役そのものは handlingms に記録済み。
        fixture.given().noPriorActivity()
                .when().command(advance("RECEIVE", false, false))
                .then().success().noEvents();
    }

    @Test
    @DisplayName("遷移表が許さない荷役では進めない（順序が入れ替わっても壊れない）")
    void doesNotSkipStates() {
        // 未受領のまま引取だけが届いた。状態を飛ばして進めると履歴が事実と食い違う。
        fixture.given().event(initialized())
                .when().command(advance("CLAIM", true, false))
                .then().success().noEvents();
    }

    @Test
    @DisplayName("不変条件 11: 取り消された荷役の分を戻す")
    void revertsTheHandling() {
        fixture.given().events(initialized(),
                        new TransportStatusUpdatedEvent(NUMBER, TransportStatus.NOT_RECEIVED,
                                TransportStatus.RECEIVED, StatusUpdateSource.HANDLING, "act-1", "JPTYO",
                                HANDLED, "handler01", NOW))
                .when().command(new RevertTrackingCommand(NUMBER, "act-1", "RECEIVE", "取り違え",
                        "handler01", NOW))
                .then().events(new TransportStatusRevertedEvent(NUMBER, TransportStatus.RECEIVED,
                        TransportStatus.NOT_RECEIVED, "RECEIVE", "取り違え", "handler01", NOW));
    }

    @Test
    @DisplayName("不変条件 11: 最後でない荷役の取り消しでは戻さない（積込済が受領済に見える）")
    void doesNotRevertWhenTheVoidedHandlingIsNotTheLast() {
        // **契約イベントは順序が入れ替わることがある。** 受領→積込と進んだあとで
        // 古い受領を取り消したとき、覚えている 1 段だけを見て戻すと、実際には
        // 船に積んである貨物が「受領済」に見える。荷主にも荷役にもそう見える。
        fixture.given().events(initialized(),
                        new TransportStatusUpdatedEvent(NUMBER, TransportStatus.NOT_RECEIVED,
                                TransportStatus.RECEIVED, StatusUpdateSource.HANDLING, "act-1",
                                "JPTYO", HANDLED, "handler01", NOW),
                        new TransportStatusUpdatedEvent(NUMBER, TransportStatus.RECEIVED,
                                TransportStatus.LOADED, StatusUpdateSource.HANDLING, "act-2",
                                "JPTYO", HANDLED, "handler01", NOW))
                .when().command(new RevertTrackingCommand(NUMBER, "act-1", "RECEIVE", "取り違え",
                        "handler01", NOW))
                .then().success().noEvents();
    }

    @Test
    @DisplayName("最後の荷役の取り消しは戻す（直前の荷役まで）")
    void revertsTheLastHandlingOnly() {
        fixture.given().events(initialized(),
                        new TransportStatusUpdatedEvent(NUMBER, TransportStatus.NOT_RECEIVED,
                                TransportStatus.RECEIVED, StatusUpdateSource.HANDLING, "act-1",
                                "JPTYO", HANDLED, "handler01", NOW),
                        new TransportStatusUpdatedEvent(NUMBER, TransportStatus.RECEIVED,
                                TransportStatus.LOADED, StatusUpdateSource.HANDLING, "act-2",
                                "JPTYO", HANDLED, "handler01", NOW))
                .when().command(new RevertTrackingCommand(NUMBER, "act-2", "LOAD", "取り違え",
                        "handler01", NOW))
                .then().events(new TransportStatusRevertedEvent(NUMBER, TransportStatus.LOADED,
                        TransportStatus.RECEIVED, "LOAD", "取り違え", "handler01", NOW));
    }

    @Test
    @DisplayName("手動更新の分は荷役の取り消しで戻さない（別の操作）")
    void doesNotRevertManualUpdates() {
        fixture.given().events(initialized(),
                        new TransportStatusUpdatedEvent(NUMBER, TransportStatus.NOT_RECEIVED,
                                TransportStatus.RECEIVED, StatusUpdateSource.MANUAL, null, "JPTYO",
                                HANDLED, "tracker01", NOW))
                .when().command(new RevertTrackingCommand(NUMBER, "act-1", "RECEIVE", "取り違え",
                        "handler01", NOW))
                .then().success().noEvents();
    }

    @Test
    @DisplayName("例外の対応中は荷役でも進めない（解決は例外の側で行う）")
    void doesNotAdvanceWhileAnExceptionIsOpen() {
        fixture.given().events(initialized(),
                        new TransportStatusUpdatedEvent(NUMBER, TransportStatus.NOT_RECEIVED,
                                TransportStatus.RECEIVED, StatusUpdateSource.MANUAL, null, "JPTYO",
                                HANDLED, "tracker01", NOW),
                        new TransportStatusUpdatedEvent(NUMBER, TransportStatus.RECEIVED,
                                TransportStatus.EXCEPTION, StatusUpdateSource.MANUAL, null, "JPTYO",
                                HANDLED, "tracker01", NOW))
                .when().command(advance("LOAD", false, false))
                .then().success().noEvents();
    }

    @Test
    @DisplayName("知らない追跡番号の取り消しでも止まらない")
    void doesNotFailToRevertUnknownTracking() {
        fixture.given().noPriorActivity()
                .when().command(new RevertTrackingCommand(NUMBER, "act-1", "RECEIVE", "理由",
                        "handler01", NOW))
                .then().success().noEvents();
    }
}
