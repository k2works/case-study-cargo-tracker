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

    @Test
    @DisplayName("移動は出発地・到着地・日時のいずれが欠けても断る")
    void movementRejectsMissingParts() {
        // null と空白は別の分岐。片方だけ見ると、もう片方を消しても緑になる。
        assertThatThrownBy(() -> new CarrierMovement(null, Location.of("USNYC"), T0, T3))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("出発地と到着地");
        assertThatThrownBy(() -> new CarrierMovement(Location.of("JPTYO"), null, T0, T3))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("出発地と到着地");
        assertThatThrownBy(() -> movement("JPTYO", "USNYC", null, T3))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("出発日時と到着日時");
        assertThatThrownBy(() -> movement("JPTYO", "USNYC", T0, null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("出発日時と到着日時");
    }

    @Test
    @DisplayName("航海番号は null も空白も断る")
    void voyageNumberRejectsNull() {
        assertThatThrownBy(() -> new VoyageNumber(null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("必須");
    }

    @Test
    @DisplayName("船名は null・空白・長すぎを断る")
    void vesselNameRejects() {
        assertThat(new VesselName("MOL EXPRESS").value()).isEqualTo("MOL EXPRESS");
        assertThatThrownBy(() -> new VesselName(" "))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("必須");
        assertThatThrownBy(() -> new VesselName("A".repeat(101)))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("100 文字以内");
    }

    @Test
    @DisplayName("前の便の到着と同時刻に出発するのは許す（停泊 0 分）")
    void allowsDepartureAtTheSameInstantAsPreviousArrival() {
        // 同じ船が着いてそのまま次の区間へ出るのは実際に起きる。
        // CarrierMovement が同時刻を断るのとは別の判断なので、ここで固定する。
        java.time.Instant t0 = java.time.Instant.parse("2026-09-10T09:00:00Z");
        java.time.Instant t1 = java.time.Instant.parse("2026-09-16T08:00:00Z");
        java.time.Instant t2 = java.time.Instant.parse("2026-09-24T18:00:00Z");

        Schedule schedule = new Schedule(List.of(
                new CarrierMovement(Location.of("JPTYO"), Location.of("SGSIN"), t0, t1),
                new CarrierMovement(Location.of("SGSIN"), Location.of("USNYC"), t1, t2)));

        assertThat(schedule.movements()).hasSize(2);
    }

    @Test
    @DisplayName("運送会社はコードも名称も null を断る")
    void carrierRejectsNull() {
        assertThat(new Carrier("MOL", "商船三井").carrierCode()).isEqualTo("MOL");
        assertThatThrownBy(() -> new Carrier(null, "商船三井"))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("運送会社コード");
        assertThatThrownBy(() -> new Carrier("MOL", null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("運送会社名");
    }

    @Test
    @DisplayName("運送会社は投影の列に入らない長さを断る")
    void carrierRejectsTooLongValues() {
        // ここで断らないと、集約を通ったイベントが投影の VARCHAR で落ちて
        // Processing Group が止まる。利用者には「登録したのに一覧に出ない」に見える。
        assertThatThrownBy(() -> new Carrier("C".repeat(21), "商船三井"))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("運送会社コードは 20 文字以内です");
        assertThatThrownBy(() -> new Carrier("MOL", "名".repeat(101)))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("運送会社名は 100 文字以内です");
    }
}
