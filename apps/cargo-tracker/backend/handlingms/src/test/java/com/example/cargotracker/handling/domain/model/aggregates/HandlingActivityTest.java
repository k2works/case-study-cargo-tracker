package com.example.cargotracker.handling.domain.model.aggregates;


import com.example.cargotracker.handling.domain.model.commands.RegisterHandlingActivityCommand;
import com.example.cargotracker.handling.domain.model.commands.VoidHandlingActivityCommand;
import com.example.cargotracker.handling.domain.model.events.ConsigneeConfirmationRecordedEvent;
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
        return new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB", "b-1", type,
                "SGSIN", type.requiresVoyageNumber() ? "V-MOL-001" : null,
                offRoute, false, null, "handler01", COMPLETED);
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
                HandlingType.UNLOAD, "SGSIN", "V-MOL-001", false, false, null, "handler01",
                NOW.plusSeconds(60));

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
                HandlingType.UNLOAD, "SGSIN", "V-MOL-001", false, false, null, "handler01", NOW);

        fixture.given().noPriorActivity()
                .when().command(justNow)
                .then().success();
    }

    @Test
    @DisplayName("不変条件 6: 過去の作業日時は通す（通信不能時は紙に控えて後から入れる）")
    void acceptsPastCompletionTime() {
        var past = new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                HandlingType.UNLOAD, "SGSIN", "V-MOL-001", false, false, null, "handler01",
                NOW.minusSeconds(86400));

        fixture.given().noPriorActivity()
                .when().command(past)
                .then().success();
    }

    @Test
    @DisplayName("不変条件 1: 積込・荷降しには航海番号が要る")
    void requiresVoyageNumberForLoadAndUnload() {
        var withoutVoyage = new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                HandlingType.LOAD, "JPTYO", null, false, false, null, "handler01", COMPLETED);

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

    private static RegisterHandlingActivityCommand claim(String consigneeName) {
        return new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB", "b-1",
                HandlingType.CLAIM, "USNYC", null, false, true, consigneeName,
                "handler01", COMPLETED);
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
                false, false, "John Smith", "handler01", COMPLETED);

        fixture.given().noPriorActivity()
                .when().command(loadWithConsignee)
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("作業者が分からない記録は残さない")
    void requiresOperator() {
        var withoutOperator = new RegisterHandlingActivityCommand(ACTIVITY, "TRK-8K2QX7M4RB",
                "b-1", HandlingType.UNLOAD, "SGSIN", "V-MOL-001", false, false, null, "  ", COMPLETED);

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
                        null, "handler01", COMPLETED)),
                new Missing("予約 ID", new RegisterHandlingActivityCommand(ACTIVITY,
                        "TRK-8K2QX7M4RB", null, HandlingType.UNLOAD, "SGSIN", "V-MOL-001",
                        false, false, null, "handler01", COMPLETED)),
                new Missing("作業場所", new RegisterHandlingActivityCommand(ACTIVITY,
                        "TRK-8K2QX7M4RB", "b-1", HandlingType.UNLOAD, "", "V-MOL-001",
                        false, false, null, "handler01", COMPLETED)),
                new Missing("作業種別", new RegisterHandlingActivityCommand(ACTIVITY,
                        "TRK-8K2QX7M4RB", "b-1", null, "SGSIN", "V-MOL-001",
                        false, false, null, "handler01", COMPLETED)),
                new Missing("作業日時", new RegisterHandlingActivityCommand(ACTIVITY,
                        "TRK-8K2QX7M4RB", "b-1", HandlingType.UNLOAD, "SGSIN", "V-MOL-001",
                        false, false, null, "handler01", null)));

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
}
