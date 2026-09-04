package com.example.cargotracker.routing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Voyage の値オブジェクト（不変条件 2・3）。 */
class VoyageValueObjectsTest {

    private static final Instant T0 = Instant.parse("2026-09-10T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-09-16T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-17T06:00:00Z");
    private static final Instant T3 = Instant.parse("2026-09-24T18:00:00Z");

    private static CarrierMovement movement(String from, String to, Instant out, Instant in) {
        return new CarrierMovement(Location.of(from), Location.of(to), out, in);
    }

    @Test
    @DisplayName("不変条件 3: 到着は出発より後")
    void arrivalMustBeAfterDeparture() {
        assertThatThrownBy(() -> movement("JPTYO", "USNYC", T3, T0))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("到着日時は出発日時より後");
        // 同時刻も断る。港の移動になっていない。
        assertThatThrownBy(() -> movement("JPTYO", "USNYC", T0, T0))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("出発地と到着地が同じ移動は断る")
    void samePortIsRejected() {
        assertThatThrownBy(() -> movement("JPTYO", "JPTYO", T0, T3))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("出発地と到着地が同じ");
    }

    @Test
    @DisplayName("不変条件 2: 連続する移動は港が繋がっている")
    void portsMustConnect() {
        assertThatThrownBy(() -> new Schedule(List.of(
                movement("JPTYO", "SGSIN", T0, T1),
                movement("USNYC", "GBLON", T2, T3))))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("寄港地が繋がっていません");
    }

    @Test
    @DisplayName("不変条件 2: 港が繋がっていても時刻が前後していれば断る")
    void timesMustNotGoBackwards() {
        // 港の連結だけを見ると、前の便より早く出る（実際には乗り継げない）航海が通る。
        assertThatThrownBy(() -> new Schedule(List.of(
                movement("JPTYO", "SGSIN", T2, T3),
                movement("SGSIN", "USNYC", T0, T1))))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("時刻が前後しています");
    }

    @Test
    @DisplayName("寄港地は 1 件以上")
    void scheduleNeedsAtLeastOneMovement() {
        assertThatThrownBy(() -> new Schedule(List.of()))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new Schedule(null))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("端点は最初の出発と最後の到着")
    void endpoints() {
        Schedule schedule = new Schedule(List.of(
                movement("JPTYO", "SGSIN", T0, T1),
                movement("SGSIN", "USNYC", T2, T3)));

        assertThat(schedule.first().departure().unLocode().value()).isEqualTo("JPTYO");
        assertThat(schedule.last().arrival().unLocode().value()).isEqualTo("USNYC");
    }

    @Test
    @DisplayName("識別子と名前は空を認めない")
    void identifiersRejectBlank() {
        assertThat(new VoyageNumber("V-MOL-001").value()).isEqualTo("V-MOL-001");
        assertThatThrownBy(() -> new VoyageNumber(" ")).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new VoyageNumber("V".repeat(21)))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new VesselName(null)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new Carrier("", "商船三井"))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new Carrier("MOL", " "))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
