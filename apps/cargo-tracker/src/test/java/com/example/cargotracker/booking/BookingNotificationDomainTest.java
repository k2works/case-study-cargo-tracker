package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingNotification;
import com.example.cargotracker.booking.domain.model.NotificationContent;
import com.example.cargotracker.booking.domain.model.NotificationResult;
import com.example.cargotracker.booking.domain.model.NotificationType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 通知の不変条件（US12）。
 *
 * <p>受け入れテスト（{@code RouteNotificationTest}）は画面からの経路を通すが、
 * <strong>そこで先に弾かれる条件は集約の守りを判別できない</strong>。
 * 本テストは集約そのものを直接壊しにいく（IT8 タスク 4-3 で判明した空振り）。
 */
@DisplayName("US12 通知の不変条件")
class BookingNotificationDomainTest {

    private static final BookingId BOOKING = BookingId.generate();
    private static final Instant SENT_AT = Instant.parse("2026-08-08T01:00:00Z");

    private NotificationContent 内容() {
        return new NotificationContent(
                List.of("SGSIN"), 20, LocalDate.of(2026, 9, 1), "TRK-20260801-0001",
                List.of("V0042"), null, 0);
    }

    /**
     * <strong>送るべき中身が無い通知を作れない。</strong>
     *
     * <p>経路が確定していない予約への通知を「送信済み」として記録すると、
     * 履歴そのものが信用できなくなる。
     */
    @Test
    void 航海番号が無い内容は組み立てられない() {
        assertThatThrownBy(() -> new NotificationContent(
                List.of(), 20, LocalDate.of(2026, 9, 1), null, List.of(), null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("経路が確定していないため通知できません");
    }

    @Test
    void 到着予定日が無い内容は組み立てられない() {
        assertThatThrownBy(() -> new NotificationContent(
                List.of(), 20, null, null, List.of("V0042"), null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("到着予定日は必須です");
    }

    /**
     * <strong>宛先の無い通知を「送信済み」として残さない。</strong>
     *
     * <p>いまは荷主のメールが DB の NOT NULL で守られているが、
     * <strong>他の宛先（荷受人・代理店）へ送る経路が増えたときに効く</strong>。
     * 集約の守りは、呼び出し側の事情が変わっても残る。
     */
    @Test
    void 宛先が無ければ通知を作れない() {
        assertThatThrownBy(() -> BookingNotification.succeeded(
                BOOKING, NotificationType.ROUTE_CONFIRMED, "  ", 内容(), SENT_AT, "sales"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("送信先のメールアドレスがありません");
    }

    /** <strong>失敗には理由が要る。</strong> 理由の無い失敗は後から調べようがない。 */
    @Test
    void 理由の無い失敗は記録できない() {
        assertThatThrownBy(() -> BookingNotification.failed(
                BOOKING, NotificationType.ROUTE_CONFIRMED, "shipper@example.com",
                内容(), SENT_AT, "sales", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("失敗の理由は必須です");
    }

    /** 成功に失敗理由は残らない。**成功なのに理由がある組み合わせを作れなくする。** */
    @Test
    void 成功した通知は失敗理由を持たない() {
        var notification = BookingNotification.succeeded(
                BOOKING, NotificationType.ROUTE_CONFIRMED, "shipper@example.com",
                内容(), SENT_AT, "sales");

        assertThat(notification.delivery().result()).isEqualTo(NotificationResult.SUCCEEDED);
        assertThat(notification.delivery().failureReason()).isNull();
        assertThat(notification.delivery().result().resendable()).isFalse();
    }

    /** 失敗した通知だけ再送できる。 */
    @Test
    void 失敗した通知は再送できる() {
        var notification = BookingNotification.failed(
                BOOKING, NotificationType.ROUTE_CONFIRMED, "shipper@example.com",
                内容(), SENT_AT, "sales", "宛先が存在しません");

        assertThat(notification.delivery().result().resendable()).isTrue();
        assertThat(notification.delivery().failureReason()).isEqualTo("宛先が存在しません");
    }

    /**
     * 期限を延ばしていれば、その差分が<strong>文面に載る</strong>。
     *
     * <p>荷主が知る必要があるのは「いつ着くか」だけではなく、
     * 当初の約束から何日ずれたかである。
     */
    @Test
    void 期限を延ばした場合は文面に差分が載る() {
        var content = new NotificationContent(
                List.of(), 25, LocalDate.of(2026, 9, 8), null, List.of("V0042"),
                LocalDate.of(2026, 9, 1), 7);

        assertThat(content.deadlineRelaxed()).isTrue();
        assertThat(content.toMessage())
                .contains("当初の希望期限: 2026-09-01")
                .contains("7 日の延長");
    }

    /** 延ばしていなければ、当初期限の行そのものを出さない。**無い差分を語らない。** */
    @Test
    void 延ばしていなければ当初期限の行は出ない() {
        assertThat(内容().toMessage()).doesNotContain("当初の希望期限");
    }
}
