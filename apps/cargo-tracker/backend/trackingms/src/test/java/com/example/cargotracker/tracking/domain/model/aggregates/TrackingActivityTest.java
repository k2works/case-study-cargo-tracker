package com.example.cargotracker.tracking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
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
        return new InitializeTrackingCommand("T-2026-0001", "b-1", "JPTYO", "USNYC", "GENERAL",
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
                .events(new TrackingInitializedEvent("T-2026-0001", "b-1", "JPTYO", "USNYC",
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
        fixture.given().events(new TrackingInitializedEvent("T-2026-0001", "b-1", "JPTYO",
                        "USNYC", "GENERAL", List.of(), NOW))
                .when().command(initialize())
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US14: 旅程の無い追跡は始めない（経路が決まってから発行される）")
    void rejectsEmptyItinerary() {
        fixture.given().noPriorActivity()
                .when().command(new InitializeTrackingCommand("T-2026-0001", "b-1",
                        "JPTYO", "USNYC", "GENERAL", List.of(), ISSUED))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US14: 予約の分からない追跡は始めない（誰の荷物か辿れなくなる）")
    void rejectsMissingBookingId() {
        fixture.given().noPriorActivity()
                .when().command(new InitializeTrackingCommand("T-2026-0001", "  ",
                        "JPTYO", "USNYC", "GENERAL", initialize().legs(), ISSUED))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US14: 予約 ID が無い（null）追跡も始めない")
    void rejectsNullBookingId() {
        // 空文字だけを試すと、null の分岐が残る。
        fixture.given().noPriorActivity()
                .when().command(new InitializeTrackingCommand("T-2026-0001", null,
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
}
