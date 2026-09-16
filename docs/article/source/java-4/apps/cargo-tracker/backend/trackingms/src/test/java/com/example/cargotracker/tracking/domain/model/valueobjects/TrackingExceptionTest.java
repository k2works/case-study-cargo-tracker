package com.example.cargotracker.tracking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.tracking.domain.model.entities.TrackingException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 例外 1 件（domain-model.md「TrackingException」/ 不変条件 6・7）。
 *
 * <p><b>解決しても消えない。</b> 事実は残り、料金調整の根拠になる。</p>
 */
class TrackingExceptionTest {

    private static final Instant OCCURRED = Instant.parse("2026-09-20T02:00:00Z");
    private static final Instant RESOLVED_AT = Instant.parse("2026-09-21T02:00:00Z");

    private static TrackingException reported(ExceptionType type) {
        return TrackingException.report("ex-1", type, OCCURRED, "SGSIN", "台風で 3 日遅れます");
    }

    @Test
    @DisplayName("起票した直後は「起票」で、まだ決着していない")
    void startsAsReported() {
        var exception = reported(ExceptionType.DELAY);

        assertThat(exception.responseStatus()).isEqualTo(ResponseStatus.REPORTED);
        assertThat(exception.settled()).isFalse();
    }

    @Test
    @DisplayName("不変条件 7: 緊急かどうかは種別が答える（属性に持たない）")
    void urgencyComesFromTheType() {
        assertThat(reported(ExceptionType.LOSS).urgent()).isTrue();
        assertThat(reported(ExceptionType.DELAY).urgent()).isFalse();
    }

    @Test
    @DisplayName("対応を始めると「対応中」になる")
    void movesToResponding() {
        var exception = reported(ExceptionType.DELAY).startResponding();

        assertThat(exception.responseStatus()).isEqualTo(ResponseStatus.RESPONDING);
        assertThat(exception.settled()).isFalse();
    }

    @Test
    @DisplayName("解決すると「解決」になり、対応内容が残る（不変条件 6）")
    void keepsTheResolution() {
        var exception = reported(ExceptionType.DELAY).startResponding()
                .resolve("代替便 V-ONE-003 に振り替えました", RESOLVED_AT);

        assertThat(exception.responseStatus()).isEqualTo(ResponseStatus.RESOLVED);
        assertThat(exception.settled()).isTrue();
        assertThat(exception.resolution()).isEqualTo("代替便 V-ONE-003 に振り替えました");
        assertThat(exception.resolvedAt()).isEqualTo(RESOLVED_AT);
        // **事実は消えない。** 起票の内容は解決後も読める。
        assertThat(exception.description()).isEqualTo("台風で 3 日遅れます");
        assertThat(exception.occurredAt()).isEqualTo(OCCURRED);
    }

    @Test
    @DisplayName("起票からいきなり解決できる（対応開始を必ず踏ませない）")
    void canResolveWithoutResponding() {
        // 現場では「連絡したらもう着いていた」がある。手順のために
        // ボタンを 2 度押させると、記録が実態から遅れる。
        var exception = reported(ExceptionType.DELAY).resolve("すでに到着済み", RESOLVED_AT);

        assertThat(exception.responseStatus()).isEqualTo(ResponseStatus.RESOLVED);
    }

    @Test
    @DisplayName("解決した例外はもう動かせない（追記のみ・不変条件 6）")
    void cannotChangeAfterResolved() {
        var resolved = reported(ExceptionType.DELAY).resolve("対応済み", RESOLVED_AT);

        assertThatThrownBy(resolved::startResponding)
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> resolved.resolve("もう一度", RESOLVED_AT))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("対応内容の無い解決は残さない（何をしたか読めない記録になる。null も空白も）")
    void requiresAResolution() {
        var exception = reported(ExceptionType.DELAY);

        assertThatThrownBy(() -> exception.resolve("  ", RESOLVED_AT))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> exception.resolve(null, RESOLVED_AT))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("発生状況が無い起票は残さない（US19 §1。null も空白も）")
    void requiresADescription() {
        assertThatThrownBy(() ->
                TrackingException.report("ex-1", ExceptionType.DELAY, OCCURRED, "SGSIN", " "))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() ->
                TrackingException.report("ex-1", ExceptionType.DELAY, OCCURRED, "SGSIN", null))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("識別子・種別・発生日時が無い起票は残さない（どの例外の話か分からなくなる）")
    void requiresTheIdentity() {
        // **値の一覧から回す。** 1 件ずつ書くと、項目を足したときに抜ける。
        record Missing(String label, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        }
        var cases = java.util.List.of(
                new Missing("例外 ID（空白）",
                        () -> TrackingException.report("  ", ExceptionType.DELAY, OCCURRED,
                                "SGSIN", "台風で 3 日遅れます")),
                // **null と空白の両方を通す。** 片方だけだと、条件の半分が未検査になる。
                new Missing("例外 ID（null）",
                        () -> TrackingException.report(null, ExceptionType.DELAY, OCCURRED,
                                "SGSIN", "台風で 3 日遅れます")),
                new Missing("例外種別",
                        () -> TrackingException.report("ex-1", null, OCCURRED,
                                "SGSIN", "台風で 3 日遅れます")),
                new Missing("発生日時",
                        () -> TrackingException.report("ex-1", ExceptionType.DELAY, null,
                                "SGSIN", "台風で 3 日遅れます")));

        for (Missing missing : cases) {
            assertThatThrownBy(missing.call())
                    .as("%s が無い起票を残すと、どの例外の話か分からなくなる", missing.label())
                    .isInstanceOf(BusinessRuleViolation.class);
        }
    }

    @Test
    @DisplayName("場所は分からないこともある（船の上で起きた破損）")
    void allowsAnUnknownLocation() {
        var exception =
                TrackingException.report("ex-1", ExceptionType.DAMAGE, OCCURRED, null, "破損");

        assertThat(exception.unLocode()).isNull();
    }
}
