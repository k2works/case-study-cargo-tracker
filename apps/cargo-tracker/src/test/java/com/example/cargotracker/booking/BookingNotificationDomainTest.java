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
                new NotificationContent.Itinerary(
                        List.of("SGSIN"), 20, LocalDate.of(2026, java.time.Month.SEPTEMBER, 1),
                        List.of("V0042")),
                "TRK-20260801-0001",
                new NotificationContent.Deadline(null, 0, 0));
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
                new NotificationContent.Itinerary(
                        List.of(), 20, LocalDate.of(2026, java.time.Month.SEPTEMBER, 1),
                        List.of()),
                null,
                new NotificationContent.Deadline(null, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("経路が確定していないため通知できません");
    }

    @Test
    void 到着予定日が無い内容は組み立てられない() {
        assertThatThrownBy(() -> new NotificationContent(
                new NotificationContent.Itinerary(
                        List.of(), 20, null,
                        List.of("V0042")),
                null,
                new NotificationContent.Deadline(null, 0, 0)))
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

    /**
     * 成功として記録される。
     *
     * <p><strong>失敗の記録は作らない</strong>（ADR-006 により外部へ送らないため、
     * 送信の失敗という事象が起こりえない）。実際に送る仕組みを入れる IT で、
     * 失敗の経路と一緒に戻す。
     */
    @Test
    void 通知は成功として記録される() {
        var notification = BookingNotification.succeeded(
                BOOKING, NotificationType.ROUTE_CONFIRMED, "shipper@example.com",
                内容(), SENT_AT, "sales");

        assertThat(notification.delivery().result()).isEqualTo(NotificationResult.SUCCEEDED);
        assertThat(notification.delivery().failureReason()).isNull();
    }

    /** 状態更新の通知は組み立て済みの文面を受け取る（US17）。 */
    @Test
    void 状態更新の通知を記録できる() {
        var notification = BookingNotification.statusUpdated(
                BOOKING, "shipper@example.com", "貨物の状態が更新されました。", SENT_AT, "tracker");

        assertThat(notification.type()).isEqualTo(NotificationType.STATUS_UPDATED);
        assertThat(notification.content()).contains("状態が更新されました");
    }

    /** 文面の無い通知は作れない。**中身の無い記録は「知らせた」ことにならない。** */
    @Test
    void 文面の無い状態更新は記録できない() {
        assertThatThrownBy(() -> BookingNotification.statusUpdated(
                BOOKING, "shipper@example.com", "  ", SENT_AT, "tracker"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("通知の文面は必須です");
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
                new NotificationContent.Itinerary(
                        List.of(), 25, LocalDate.of(2026, java.time.Month.SEPTEMBER, 8),
                        List.of("V0042")),
                null,
                new NotificationContent.Deadline(LocalDate.of(2026, java.time.Month.SEPTEMBER, 1), 7, 0));

        assertThat(content.deadlineRelaxed()).isTrue();
        assertThat(content.toMessage())
                .contains("当初の希望期限: 2026-09-01")
                .contains("7 日の延長");
    }

    /**
     * <strong>希望期限を超える場合は何日遅れるかを文面に書く</strong>（US28）。
     *
     * <p>「遅れます」だけでは、荷主は受け入れるか手配し直すかを判断できない。
     * 誤配の再設計では、当初の期限に間に合わないことが普通に起きる。
     */
    @Test
    void 希望期限を超える場合は遅れる日数が文面に載る() {
        var content = new NotificationContent(
                new NotificationContent.Itinerary(
                        List.of(), 25, LocalDate.of(2026, java.time.Month.SEPTEMBER, 8),
                        List.of("V0042")),
                null,
                new NotificationContent.Deadline(null, 0, 5));

        assertThat(content.overshootsDeadline()).isTrue();
        assertThat(content.toMessage()).contains("5 日遅れる見込み");
    }

    /** <strong>間に合うなら書かない。</strong> 常に書く実装でも緑になる形にしない。 */
    @Test
    void 希望期限に間に合えば遅れの記述は載らない() {
        var content = new NotificationContent(
                new NotificationContent.Itinerary(
                        List.of(), 25, LocalDate.of(2026, java.time.Month.SEPTEMBER, 8),
                        List.of("V0042")),
                null,
                new NotificationContent.Deadline(null, 0, 0));

        assertThat(content.overshootsDeadline()).isFalse();
        assertThat(content.toMessage()).doesNotContain("遅れる見込み");
    }

    /** 延ばしていなければ、当初期限の行そのものを出さない。**無い差分を語らない。** */
    @Test
    void 延ばしていなければ当初期限の行は出ない() {
        assertThat(内容().toMessage()).doesNotContain("当初の希望期限");
    }
}
