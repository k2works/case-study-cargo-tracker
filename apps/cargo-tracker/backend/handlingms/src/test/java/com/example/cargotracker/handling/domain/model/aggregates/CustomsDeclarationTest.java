package com.example.cargotracker.handling.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.domain.model.commands.RegisterCustomsDeclarationCommand;
import com.example.cargotracker.handling.domain.model.commands.UpdateCustomsStatusCommand;
import com.example.cargotracker.handling.domain.model.events.CustomsDeclarationRegisteredEvent;
import com.example.cargotracker.handling.domain.model.events.CustomsStatusUpdatedEvent;
import com.example.cargotracker.handling.domain.model.valueobjects.CustomsStatus;
import com.example.cargotracker.shared.contract.event.CustomsStatusChangedEvent;
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
 * 通関申告（UC21 / US29）。
 *
 * <p><b>1 申告 1 集約。</b> 「未決着は貨物あたり高々 1 件」（不変条件 3）は集約から
 * 見えない——他の申告を知らないので、application 層が守る（IT9 の 5 分規則と同じ形）。</p>
 */
class CustomsDeclarationTest {

    private static final Instant NOW = Instant.parse("2026-10-05T02:00:00Z");
    private static final Instant DECLARED = Instant.parse("2026-10-01T09:00:00Z");
    private static final String NUMBER = "IMP-2026-0001";
    private static final String TRACKING = "TRK-8K2QX7M4RB";

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(
                        String.class, CustomsDeclaration.class))
                .componentRegistry(registry -> registry.registerComponent(
                        Clock.class, c -> Clock.fixed(NOW, ZoneId.of("Asia/Tokyo"))));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static RegisterCustomsDeclarationCommand register() {
        return new RegisterCustomsDeclarationCommand(NUMBER, TRACKING, "b-1", DECLARED,
                "handler01");
    }

    private static CustomsDeclarationRegisteredEvent registered() {
        return new CustomsDeclarationRegisteredEvent(NUMBER, TRACKING, "b-1", DECLARED,
                "handler01", NOW);
    }

    private static UpdateCustomsStatusCommand update(CustomsStatus status, String reason) {
        return new UpdateCustomsStatusCommand(NUMBER, status, reason, "tracker01");
    }

    private static CustomsStatusUpdatedEvent updated(CustomsStatus from, CustomsStatus to,
            String reason) {
        return new CustomsStatusUpdatedEvent(NUMBER, from.name(), to.name(), reason,
                "tracker01", NOW);
    }

    private static CustomsStatusChangedEvent changed(CustomsStatus from, CustomsStatus to,
            String reason, int heldBusinessDays) {
        return new CustomsStatusChangedEvent(NUMBER, TRACKING, "b-1", from.name(), to.name(),
                reason, heldBusinessDays, "tracker01", NOW);
    }

    @Test
    @DisplayName("US29 §1: 登録すると審査中になる")
    void registersAsPending() {
        fixture.given().noPriorActivity()
                .when().command(register())
                .then().events(registered());
    }

    @Test
    @DisplayName("不変条件 1: 追跡番号・申告日時は必須（申告番号の書式は検査しない）")
    void requiresTrackingNumberAndDeclaredAt() {
        fixture.given().noPriorActivity()
                .when().command(new RegisterCustomsDeclarationCommand(NUMBER, null, "b-1",
                        DECLARED, "handler01"))
                .then().exception(BusinessRuleViolation.class);

        fixture.given().noPriorActivity()
                .when().command(new RegisterCustomsDeclarationCommand(NUMBER, TRACKING, "b-1",
                        null, "handler01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("同じ申告番号で 2 度登録できない（税関が採番した番号は 1 つの申告を指す）")
    void refusesDuplicateRegistration() {
        fixture.given().event(registered())
                .when().command(register())
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US29 §2: 理由を添えて通関済に更新でき、契約イベントも出る")
    void updatesStatusWithReason() {
        // **内部と契約の 2 本立て。** 集約の復元は内部イベント、他 BC は契約を読む。
        // **通関済では通知の記録も残す**（§4。送信基盤はスコープ外）。
        fixture.given().event(registered())
                .when().command(update(CustomsStatus.CLEARED, "書類に不備なし"))
                .then().eventsSatisfy(events -> assertThat(events)
                        .extracting(event -> event.payload().getClass().getSimpleName())
                        .containsExactly("CustomsStatusUpdatedEvent",
                                "CustomsStatusChangedEvent",
                                "CustomsClearanceNotifiedEvent"));
    }

    @Test
    @DisplayName("US29 §2: 理由の無い更新は断る（何が起きたか読めない記録を残さない）")
    void refusesUpdateWithoutReason() {
        fixture.given().event(registered())
                .when().command(update(CustomsStatus.CLEARED, "  "))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("同じ状態への更新は断る（履歴に意味の無い行を積まない）")
    void refusesUpdateToTheSameStatus() {
        fixture.given().event(registered())
                .when().command(update(CustomsStatus.PENDING, "変わっていません"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US29 §5: 留置にすると契約イベントが出る（税関保留の起票は購読側）")
    void publishesContractEventWhenHeld() {
        fixture.given().event(registered())
                .when().command(update(CustomsStatus.HELD, "原産地証明が未提出"))
                .then().events(updated(CustomsStatus.PENDING, CustomsStatus.HELD,
                                "原産地証明が未提出"),
                        changed(CustomsStatus.PENDING, CustomsStatus.HELD,
                                "原産地証明が未提出", 0));
    }

    @Test
    @DisplayName("不変条件 4: 留置から出るときは営業日数を載せる（Billing の調整根拠）")
    void carriesHeldBusinessDaysWhenLeavingHeld() {
        // 2026-10-01(木) に留置 → 2026-10-05(月) に解除。営業日は 2 日（金・月）。
        // **暦日なら 4 日。** 3 日超の督促が実際より早く点いてしまう。
        fixture.given()
                .event(registered())
                .event(new CustomsStatusUpdatedEvent(NUMBER, "PENDING", "HELD",
                        "原産地証明が未提出", "tracker01", Instant.parse("2026-10-01T02:00:00Z")))
                .when().command(update(CustomsStatus.CLEARED, "証明書を受領"))
                .then().eventsSatisfy(events -> assertThat(events)
                        .map(event -> event.payload())
                        .filteredOn(CustomsStatusChangedEvent.class::isInstance)
                        .map(CustomsStatusChangedEvent.class::cast)
                        .singleElement()
                        .extracting(CustomsStatusChangedEvent::heldBusinessDays)
                        .isEqualTo(2));
    }

    @Test
    @DisplayName("決着したあとは動かせない（通関済からは出し直せない）")
    void refusesUpdateAfterCleared() {
        fixture.given().event(registered())
                .event(updated(CustomsStatus.PENDING, CustomsStatus.CLEARED, "不備なし"))
                .when().command(update(CustomsStatus.HELD, "やっぱり留置"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("不可のあとは留置へ動かせない（出し直しは新しい申告で行う）")
    void refusesUpdateAfterRejected() {
        fixture.given().event(registered())
                .event(updated(CustomsStatus.PENDING, CustomsStatus.REJECTED, "通らなかった"))
                .when().command(update(CustomsStatus.HELD, "留置に戻す"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("留置からは通関済にも不可にもできる（不備の解消・不通過）")
    void allowsBothOutcomesFromHeld() {
        fixture.given().event(registered())
                .event(updated(CustomsStatus.PENDING, CustomsStatus.HELD, "検査待ち"))
                .when().command(update(CustomsStatus.REJECTED, "通らなかった"))
                .then().success();
    }

    @Test
    @DisplayName("登録されていない申告は更新できない（税関の採番より先には動かせない）")
    void refusesUpdateOfUnregisteredDeclaration() {
        fixture.given().noPriorActivity()
                .when().command(update(CustomsStatus.CLEARED, "書類に不備なし"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("通関状態の無い更新は断る（何に変えるのか決まらない）")
    void refusesUpdateWithoutStatus() {
        fixture.given().event(registered())
                .when().command(update(null, "書類に不備なし"))
                .then().exception(BusinessRuleViolation.class);
    }
}
