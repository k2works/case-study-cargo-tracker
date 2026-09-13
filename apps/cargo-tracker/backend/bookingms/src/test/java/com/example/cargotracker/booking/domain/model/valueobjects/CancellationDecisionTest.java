package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * キャンセル申請への判断（US30 §受入基準 5・7 / 不変条件 9-2）。
 *
 * <p><b>承認とは「どこで降ろすか」を決めること</b>である。決めずに承認しても、
 * 貨物は船の上に残る。</p>
 */
class CancellationDecisionTest {

    private static final Instant AT = Instant.parse("2026-09-25T02:00:00Z");
    private static final Location TOKYO = Location.of("JPTYO");
    private static final Location SINGAPORE = Location.of("SGSIN");
    private static final Location NEW_YORK = Location.of("USNYC");
    private static final Location LONDON = Location.of("GBLON");

    @Test
    @DisplayName("残りの寄港地なら承認できる")
    void approvesARemainingPort() {
        var decision = CancellationDecision.approve(SINGAPORE, TOKYO,
                List.of(SINGAPORE, NEW_YORK), "荷主の指定倉庫が近い", "tracker01", AT);

        assertThat(decision.approved()).isTrue();
        assertThat(decision.dischargeLocation()).isEqualTo(SINGAPORE);
    }

    @Test
    @DisplayName("現在地でも承認できる（いま居る港で降ろす）")
    void approvesTheCurrentPort() {
        assertThat(CancellationDecision.approve(TOKYO, TOKYO, List.of(SINGAPORE, NEW_YORK),
                null, "tracker01", AT).dischargeLocation())
                .isEqualTo(TOKYO);
    }

    @Test
    @DisplayName("旅程に無い港は断り、指定できる港を添える（0 件から選ばせない）")
    void refusesAPortOutsideTheItinerary() {
        assertThatThrownBy(() -> CancellationDecision.approve(LONDON, TOKYO,
                List.of(SINGAPORE, NEW_YORK), null, "tracker01", AT))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("GBLON")
                .as("どこなら指定できるかが分からないと、次に何をすればよいか決められない")
                .hasMessageContaining("SGSIN");
    }

    @Test
    @DisplayName("通過済みの港は残りに入らないので断られる（船はもう戻らない）")
    void refusesAPassedPort() {
        // 東京を出て シンガポールへ向かっている。**東京は「残り」ではない**が、
        // 現在地が東京のあいだは指定できる——区別しているのは「いま居るか」である。
        assertThatThrownBy(() -> CancellationDecision.approve(TOKYO, SINGAPORE,
                List.of(NEW_YORK), null, "tracker01", AT))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("陸揚げ地の無い承認は断る（決めずに承認しても船の上に残る）")
    void refusesApprovalWithoutADischargePort() {
        assertThatThrownBy(() -> CancellationDecision.approve(null, TOKYO,
                List.of(SINGAPORE), null, "tracker01", AT))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("却下には理由が要る（申請した営業が次の手を決められない）")
    void requiresAReasonToReject() {
        assertThat(CancellationDecision.reject("荷受人がすでに手配済み", "tracker01", AT))
                .satisfies(decision -> {
                    assertThat(decision.approved()).isFalse();
                    assertThat(decision.dischargeLocation())
                            .as("却下は輸送を続ける判断なので、降ろす港は無い").isNull();
                });

        assertThatThrownBy(() -> CancellationDecision.reject("  ", "tracker01", AT))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("判断した人は必須（誰が決めたか分からない記録は履歴にならない）")
    void requiresTheDecider() {
        assertThatThrownBy(() -> CancellationDecision.approve(SINGAPORE, TOKYO,
                List.of(SINGAPORE), null, "  ", AT))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> CancellationDecision.reject("理由", null, AT))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
