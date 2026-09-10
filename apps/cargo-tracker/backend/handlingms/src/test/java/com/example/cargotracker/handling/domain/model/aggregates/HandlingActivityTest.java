package com.example.cargotracker.handling.domain.model.aggregates;


import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.domain.model.commands.RegisterHandlingActivityCommand;
import com.example.cargotracker.handling.domain.model.commands.VoidHandlingActivityCommand;
import com.example.cargotracker.handling.domain.model.events.ConsigneeConfirmationRecordedEvent;
import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.contract.event.HandlingActivityVoidedEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 荷役の記録（UC13 / US15）。<b>handlingms の最初の集約</b>。
 *
 * <p><b>予定ルート外でも拒まない。</b> 現場ではすでに作業が終わっている。</p>
 */
class HandlingActivityTest {

    private static final Instant NOW = Instant.parse("2026-09-16T08:35:00Z");
    private static final Instant COMPLETED = Instant.parse("2026-09-16T08:30:00Z");
    private static final String ACTIVITY = "act-1";

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(
                        String.class, HandlingActivity.class))
                .componentRegistry(registry -> registry.registerComponent(
                        Clock.class, c -> Clock.fixed(NOW, ZoneId.of("Asia/Tokyo"))));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static RegisterHandlingActivityCommand register(HandlingType type, boolean offRoute) {
        return register(type, offRoute, null, null);
    }

    /** 通関状態を指定して組む（引取のガード / US29 §受入基準 3）。 */
    private static RegisterHandlingActivityCommand register(HandlingType type, boolean offRoute,
            String consigneeName, CustomsStatus customsStatus) {
        return new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB", "b-1", type,
                "SGSIN", type.requiresVoyageNumber() ? "V-MOL-001" : null,
                offRoute, false, consigneeName, customsStatus, NOW, "handler01", COMPLETED);
    }

    private static HandlingActivityRegisteredEvent registered(HandlingType type, boolean offRoute) {
        return new HandlingActivityRegisteredEvent(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                type.name(), "SGSIN", type.requiresVoyageNumber() ? "V-MOL-001" : null,
                offRoute, false, "handler01", COMPLETED, NOW);
    }

    @Test
    @DisplayName("US15 §2・§3: 種別・場所・日時を記録できる")
    void registersHandling() {
        fixture.given().noPriorActivity()
                .when().command(register(HandlingType.UNLOAD, false))
                .then().events(registered(HandlingType.UNLOAD, false));
    }

    @Test
    @DisplayName("US15 §7: 予定ルート外でも記録は拒まない（現場では作業が終わっている）")
    void recordsOffRouteHandling() {
        fixture.given().noPriorActivity()
                .when().command(register(HandlingType.UNLOAD, true))
                .then().events(registered(HandlingType.UNLOAD, true));
    }

    @Test
    @DisplayName("不変条件 5: 同じ活動 ID の再送は二重に記録しない")
    void isIdempotentForTheSameActivityId() {
        // **断らない。** 断ると、通信断で再送した現場に「二重に記録された」と
        // 誤解させる。イベントを足さずに同じ応答を返す。
        fixture.given().event(registered(HandlingType.UNLOAD, false))
                .when().command(register(HandlingType.UNLOAD, false))
                .then().success().noEvents();
    }

    @Test
    @DisplayName("不変条件 6: 未来の作業日時は拒む（過去は通す）")
    void rejectsFutureCompletionTime() {
        var future = new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                HandlingType.UNLOAD, "SGSIN", "V-MOL-001", false, false, null, null, NOW, "handler01", NOW.plusSeconds(60));

        fixture.given().noPriorActivity()
                .when().command(future)
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("不変条件 6: いまちょうどの作業日時は通す（境界。isAfter を !isBefore にすると赤）")
    void acceptsCompletionTimeExactlyNow() {
        // **境界が未固定だと、判定を「以後は拒む」に変異させても緑になる。**
        // 現場は作業を終えた直後に記録する——ちょうどいまを拒むと、
        // その瞬間に押した記録が通らない。
        var justNow = new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                HandlingType.UNLOAD, "SGSIN", "V-MOL-001", false, false, null, null, NOW, "handler01", NOW);

        fixture.given().noPriorActivity()
                .when().command(justNow)
                .then().success();
    }

    @Test
    @DisplayName("不変条件 6: 過去の作業日時は通す（通信不能時は紙に控えて後から入れる）")
    void acceptsPastCompletionTime() {
        var past = new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                HandlingType.UNLOAD, "SGSIN", "V-MOL-001", false, false, null, null, NOW, "handler01",
                NOW.minusSeconds(86400));

        fixture.given().noPriorActivity()
                .when().command(past)
                .then().success();
    }

    @Test
    @DisplayName("不変条件 1: 積込・荷降しには航海番号が要る")
    void requiresVoyageNumberForLoadAndUnload() {
        var withoutVoyage = new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                HandlingType.LOAD, "JPTYO", null, false, false, null, null, NOW, "handler01", COMPLETED);

        fixture.given().noPriorActivity()
                .when().command(withoutVoyage)
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("不変条件 1: 受領には航海番号が要らない（船に紐づかない作業）")
    void doesNotRequireVoyageNumberForReceive() {
        fixture.given().noPriorActivity()
                .when().command(register(HandlingType.RECEIVE, false))
                .then().success();
    }

    // ---- US16 引取（IT10 T2） ----

    /** 通関済の貨物の引取。**通関のガード（US29・IT12）は別のテストで見る。** */
    private static RegisterHandlingActivityCommand claim(String consigneeName) {
        return claim(consigneeName, CustomsStatus.CLEARED);
    }

    private static RegisterHandlingActivityCommand claim(String consigneeName,
            CustomsStatus customsStatus) {
        return new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                HandlingType.CLAIM, "USNYC", null, false, true, consigneeName,
                customsStatus, NOW, "handler01", COMPLETED);
    }

    @Test
    @DisplayName("US16 §2: 荷受人の確認が取れていない引取は記録できない（不変条件 1）")
    void rejectsClaimWithoutConsigneeConfirmation() {
        // **IT9 は引取そのものを断っていた。** 検査できない段階で
        // DELIVERED——精算の開始条件——へ進む経路を開けないため。
        // 本 IT で開けるのは、荷受人の確認という検査を同時に入れるからである。
        fixture.given().noPriorActivity()
                .when().command(claim(null))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US16 §2: 空白だけの確認も取れていないものとして断る")
    void rejectsClaimWithBlankConsigneeConfirmation() {
        fixture.given().noPriorActivity()
                .when().command(claim("   "))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US16 §2: 荷受人の確認が取れた引取は記録され、確認の事実も残る")
    void recordsClaimWithConsigneeConfirmation() {
        fixture.given().noPriorActivity()
                .when().command(claim("John Smith"))
                .then().events(
                        new HandlingActivityRegisteredEvent(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                                "CLAIM", "USNYC", null, false, true, "handler01",
                                COMPLETED, NOW),
                        new ConsigneeConfirmationRecordedEvent(ACTIVITY, "TRK-8K2QX7M4RB",
                                "John Smith", NOW));
    }

    @Test
    @DisplayName("荷受人の確認は引取にしか載せない（載せたら断る。黙って捨てない）")
    void rejectsConsigneeConfirmationOnOtherTypes() {
        var loadWithConsignee = new RegisterHandlingActivityCommand(ACTIVITY,
                "TRK-8K2QX7M4RB", "b-1", HandlingType.LOAD, "JPTYO", "V-MOL-001",
                false, false, "John Smith", null, NOW, "handler01", COMPLETED);

        fixture.given().noPriorActivity()
                .when().command(loadWithConsignee)
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("作業者が分からない記録は残さない")
    void requiresOperator() {
        var withoutOperator = new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB",
                "b-1", HandlingType.UNLOAD, "SGSIN", "V-MOL-001", false, false, null, null, NOW, "  ", COMPLETED);

        fixture.given().noPriorActivity()
                .when().command(withoutOperator)
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("不変条件 7: 取り消すと事実が増える（元の記録は消えない）")
    void voidsTheActivity() {
        fixture.given().event(registered(HandlingType.UNLOAD, false))
                .when().command(new VoidHandlingActivityCommand(ACTIVITY, "取り違えました",
                        "handler01"))
                .then().events(new HandlingActivityVoidedEvent(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                        "UNLOAD", "取り違えました", "handler01", NOW));
    }

    @Test
    @DisplayName("不変条件 7: 取り消し済みの再取り消しは断る")
    void rejectsDoubleVoid() {
        fixture.given().events(registered(HandlingType.UNLOAD, false),
                        new HandlingActivityVoidedEvent(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                                "UNLOAD", "取り違えました", "handler01", NOW))
                .when().command(new VoidHandlingActivityCommand(ACTIVITY, "もう一度", "handler01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("不変条件 7: 取り消しの理由は必須")
    void requiresAReasonToVoid() {
        fixture.given().event(registered(HandlingType.UNLOAD, false))
                .when().command(new VoidHandlingActivityCommand(ACTIVITY, "  ", "handler01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("記録されていない荷役は取り消せない")
    void rejectsVoidBeforeRegistration() {
        fixture.given().noPriorActivity()
                .when().command(new VoidHandlingActivityCommand(ACTIVITY, "理由", "handler01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("必須の項目が欠けた記録は残さない（あとから誰も突き合わせられない）")
    void requiresTheEssentialFields() {
        record Missing(String label, RegisterHandlingActivityCommand command) {
        }

        var cases = java.util.List.of(
                new Missing("追跡番号", new RegisterHandlingActivityCommand(ACTIVITY, "  ", "b-1",
                        HandlingType.UNLOAD, "SGSIN", "V-MOL-001", false, false,
                        null, null, NOW, "handler01", COMPLETED)),
                new Missing("予約 ID", new RegisterHandlingActivityCommand(ACTIVITY,
                        "TRK-8K2QX7M4RB", null, HandlingType.UNLOAD, "SGSIN", "V-MOL-001",
                        false, false, null, null, NOW, "handler01", COMPLETED)),
                new Missing("作業場所", new RegisterHandlingActivityCommand(ACTIVITY,
                        "TRK-8K2QX7M4RB", "b-1", HandlingType.UNLOAD, "", "V-MOL-001",
                        false, false, null, null, NOW, "handler01", COMPLETED)),
                new Missing("作業種別", new RegisterHandlingActivityCommand(ACTIVITY,
                        "TRK-8K2QX7M4RB", "b-1", null, "SGSIN", "V-MOL-001",
                        false, false, null, null, NOW, "handler01", COMPLETED)),
                new Missing("作業日時", new RegisterHandlingActivityCommand(ACTIVITY,
                        "TRK-8K2QX7M4RB", "b-1", HandlingType.UNLOAD, "SGSIN", "V-MOL-001",
                        false, false, null, null, NOW, "handler01", null)));

        for (var missing : cases) {
            fixture.given().noPriorActivity()
                    .when().command(missing.command())
                    .then().exception(BusinessRuleViolation.class);
        }
    }

    @Test
    @DisplayName("取り消した人が分からない記録は残さない")
    void requiresWhoVoided() {
        fixture.given().event(registered(HandlingType.UNLOAD, false))
                .when().command(new VoidHandlingActivityCommand(ACTIVITY, "理由", null))
                .then().exception(BusinessRuleViolation.class);
    }
    // ---- US29 通関のガード（IT12 T7） ----

    @Test
    @DisplayName("US29 §3: 通関済でない貨物の引取は断り、判定に使った状態を返す")
    void rejectsClaimUntilCustomsCleared() {
        // **緩めていない側**（Try T2）。通関済以外はすべて断る。値の一覧から
        // 回す——足した状態をここで扱い忘れると、その状態だけ引取が素通りする。
        for (CustomsStatus status : CustomsStatus.values()) {
            if (status.allowsClaim()) {
                continue;
            }
            fixture.given().noPriorActivity()
                    .when().command(claim("John Smith", status))
                    .then().exception(IllegalTransition.class);
        }
    }

    @Test
    @DisplayName("US29 §3: 断りの理由に通関状態と判定時点が入る（画面が再確認へ導ける）")
    void tellsWhichCustomsStatusRefusedTheClaim() {
        fixture.given().noPriorActivity()
                .when().command(claim("John Smith", CustomsStatus.PENDING))
                .then().exceptionSatisfies(thrown -> assertThat(thrown.getMessage())
                        // **内部名は出さない**（IT12 レビュー 高）。現場が読む文に
                        // `PENDING` が混ざっても意味が増えない。
                        .doesNotContain("PENDING")
                        .contains("審査中")
                        // **時点は業務タイムゾーンで出す。** `Instant#toString` の
                        // UTC を出すと、港に居る人が自分の時計との 9 時間差に
                        // 気づけない。ここを落とすと画面が再確認へ導けない。
                        .doesNotContain("Z")
                        .contains("2026/09/16 17:35"));
    }

    @Test
    @DisplayName("US29 §3: 申告が無い貨物は「申告がありません」と断る（審査中とは違う）")
    void rejectsClaimWhenNoDeclaration() {
        // 「無い」と「審査中」は違う。前者はまだ申告していないので荷役作業員が
        // 申告から始める。後者は税関を待つしかない。
        fixture.given().noPriorActivity()
                .when().command(claim("John Smith", null))
                .then().exceptionSatisfies(thrown ->
                        assertThat(thrown.getMessage()).contains("通関申告がありません"));
    }

    @Test
    @DisplayName("通関のガードは引取だけ（荷降しは通関を待たない）")
    void doesNotGuardOtherTypes() {
        // **緩めた側の範囲を確かめる**（Try T2）。ここを引取以外にも広げると、
        // 輸入港に着く前の積込・荷降しが記録できなくなる。
        fixture.given().noPriorActivity()
                .when().command(register(HandlingType.UNLOAD, false, null, null))
                .then().success();
    }
}
