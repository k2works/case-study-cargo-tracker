package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 荷主への通知の記録（US12-4・[ADR-021] 決定 1）。 */
@DisplayName("経路の通知記録")
class RouteNotificationTest {

    private static final Instant NOTIFIED_AT = Instant.parse("2026-08-22T02:00:00Z");

    @Test
    @DisplayName("いつ・誰が の 1 組で記録する")
    void recordsWhenAndWho() {
        RouteNotification notification = RouteNotification.of(NOTIFIED_AT, "sales01");

        assertThat(notification.notifiedAt()).isEqualTo(NOTIFIED_AT);
        assertThat(notification.notifiedBy()).isEqualTo("sales01");
    }

    /**
     * 片方だけでは業務上の意味が無い。
     *
     * <p>日時だけでは誰に聞けばよいか分からず、担当者だけではいつの話か分からない。
     */
    @Test
    @DisplayName("日時が無ければ受け付けない")
    void rejectsMissingTimestamp() {
        assertThatThrownBy(() -> RouteNotification.of(null, "sales01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("日時");
    }

    @Test
    @DisplayName("担当者が無ければ受け付けない")
    void rejectsMissingOperator() {
        assertThatThrownBy(() -> RouteNotification.of(NOTIFIED_AT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("担当者");
    }

    @Test
    @DisplayName("担当者が空白だけでも受け付けない")
    void rejectsBlankOperator() {
        assertThatThrownBy(() -> RouteNotification.of(NOTIFIED_AT, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * <strong>復元では検査しない</strong>（[ADR-012]）。
     *
     * <p>検査を後から足すと、その規則が無かったころの行が読めなくなる。守るのは
     * 新しく受け入れるときだけでよい。<strong>`of` の検査だけを固定すると、この規律は
     * 守られているように見えて実は無検査になる</strong>ため、対で確かめる。
     */
    @Test
    @DisplayName("復元では検査しない（規則が無かったころの行も読める）")
    void restoreDoesNotValidate() {
        assertThat(RouteNotification.restore(NOTIFIED_AT, ""))
                .isEqualTo(new RouteNotification(NOTIFIED_AT, ""));
    }

    /**
     * 通知していない予約は「記録が無い」。
     *
     * <p>空の記録を作ると、「通知したが日時が分からない」と区別できなくなる。
     */
    @Test
    @DisplayName("両方が空なら記録は無い")
    void restoresNothingWhenBothAreAbsent() {
        assertThat(RouteNotification.restore(null, null)).isNull();
    }
}
