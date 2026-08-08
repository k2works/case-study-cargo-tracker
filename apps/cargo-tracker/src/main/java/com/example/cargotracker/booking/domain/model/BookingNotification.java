package com.example.cargotracker.booking.domain.model;

import java.time.Instant;

/**
 * 荷主への通知の送信記録（US12）。Booking Context の集約ルート。
 *
 * <p><strong>これが「通知」の実体である。</strong> ADR-006 により外部への送信は行わない
 * （内部シミュレーション）。送った事実を残すことが、この機能の価値そのものである。
 *
 * <p><strong>失敗も記録する。</strong> 失敗を捨てると「送ったが届かなかった」を追えない。
 */
public final class BookingNotification {

    private final Long id;
    private final BookingId bookingId;
    private final NotificationType type;
    private final String recipientEmail;
    private final String content;
    private final NotificationDelivery delivery;

    private BookingNotification(
            Long id, BookingId bookingId, NotificationType type,
            String recipientEmail, String content, NotificationDelivery delivery) {
        this.id = id;
        this.bookingId = bookingId;
        this.type = type;
        this.recipientEmail = recipientEmail;
        this.content = content;
        this.delivery = delivery;
    }

    /**
     * 送信できたことを記録する。
     *
     * <p>宛先が無い予約には作れない。<strong>宛先の無い通知を「送信済み」として
     * 残すと、履歴が信用できなくなる。</strong>
     */
    public static BookingNotification succeeded(
            BookingId bookingId, NotificationType type, String recipientEmail,
            NotificationContent content, Instant sentAt, String sentBy) {
        requireRecipient(recipientEmail);
        return new BookingNotification(null, bookingId, type, recipientEmail,
                content.toMessage(), NotificationDelivery.succeeded(sentAt, sentBy));
    }

    /** 送信できなかったことを記録する。**理由を必ず持つ。** */
    public static BookingNotification failed(
            BookingId bookingId, NotificationType type, String recipientEmail,
            NotificationContent content, Instant sentAt, String sentBy, String reason) {
        requireRecipient(recipientEmail);
        return new BookingNotification(null, bookingId, type, recipientEmail,
                content.toMessage(), NotificationDelivery.failed(sentAt, sentBy, reason));
    }

    /** 永続化された記録から復元する。 */
    public static BookingNotification reconstruct(
            Long id, BookingId bookingId, NotificationType type,
            String recipientEmail, String content, NotificationDelivery delivery) {
        return new BookingNotification(id, bookingId, type, recipientEmail, content, delivery);
    }

    private static void requireRecipient(String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("送信先のメールアドレスがありません");
        }
    }

    public Long id() {
        return id;
    }

    public BookingId bookingId() {
        return bookingId;
    }

    public NotificationType type() {
        return type;
    }

    public String recipientEmail() {
        return recipientEmail;
    }

    public String content() {
        return content;
    }

    /** 送信の事実（日時・送信者・結果・失敗理由）。 */
    public NotificationDelivery delivery() {
        return delivery;
    }
}
