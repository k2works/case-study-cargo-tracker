package com.example.cargotracker.booking.domain.model.aggregates;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.NotificationContent;
import com.example.cargotracker.booking.domain.model.valueobjects.NotificationDelivery;
import com.example.cargotracker.booking.domain.model.valueobjects.NotificationType;

import java.time.Instant;

/**
 * 荷主への通知の送信記録（US12）。Booking Context の集約ルート。
 *
 * <p><strong>これが「通知」の実体である。</strong> ADR-006 により外部への送信は行わない
 * （内部シミュレーション）。送った事実を残すことが、この機能の価値そのものである。
 *
 * <p><strong>記録は当社側の操作記録であり、荷主への到達を保証しない。</strong>
 * 外部送信が無い以上、送信の失敗という事象も起こりえない。
 * <strong>失敗を記録する経路は、実際に送る仕組みを入れる IT で足す</strong>
 * （それまで「失敗も記録する」と書くと、失敗を検知できる仕組みがあると読めてしまう）。
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

    /**
     * 貨物状態の更新を知らせた記録（US17）。
     *
     * <p>経路の通知（{@link #succeeded}）と違い、<strong>組み立て済みの文面を受け取る</strong>。
     * 状態の更新には経由港も所要日数も無く、{@link NotificationContent} の形に載らない。
     */
    public static BookingNotification statusUpdated(
            BookingId bookingId, String recipientEmail, String message,
            Instant sentAt, String sentBy) {
        requireRecipient(recipientEmail);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("通知の文面は必須です");
        }
        return new BookingNotification(null, bookingId, NotificationType.STATUS_UPDATED,
                recipientEmail, message, NotificationDelivery.succeeded(sentAt, sentBy));
    }

    /**
     * 例外の発生・対応報告を知らせた記録（US19 / US20）。
     *
     * <p>状態更新（{@link #statusUpdated}）と同じく<strong>組み立て済みの文面を
     * 受け取る</strong>。種別を引数で受けるのは、発生と対応報告で
     * <strong>荷主に届く意味が違う</strong>ためである。
     */
    public static BookingNotification exception(
            BookingId bookingId, NotificationType type, String recipientEmail,
            String message, Instant sentAt, String sentBy) {
        requireRecipient(recipientEmail);
        if (type != NotificationType.EXCEPTION_RAISED
                && type != NotificationType.EXCEPTION_RESOLVED) {
            throw new IllegalArgumentException("例外の通知種別ではありません: " + type);
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("通知の文面は必須です");
        }
        return new BookingNotification(null, bookingId, type,
                recipientEmail, message, NotificationDelivery.succeeded(sentAt, sentBy));
    }

    /**
     * 通関完了の通知（US29）。
     *
     * <p><strong>例外の通知とは別の入口にする。</strong> {@link #exception} の検査を
     * 緩めて種別を通すこともできたが、そうすると<strong>「例外の通知種別ではありません」
     * という守りが何も守らなくなる</strong>。種別ごとに入口を分けているのは、
     * 呼び出し側が意味を取り違えたときに型と検査で気づけるようにするためである。
     */
    public static BookingNotification customsCleared(
            BookingId bookingId, String recipientEmail,
            String message, Instant sentAt, String sentBy) {
        requireRecipient(recipientEmail);
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("通知の文面は必須です");
        }
        return new BookingNotification(null, bookingId, NotificationType.CUSTOMS_CLEARED,
                recipientEmail, message, NotificationDelivery.succeeded(sentAt, sentBy));
    }

    /**
     * 引取確認コードの再伝達（US35 / C7）。
     *
     * <p><strong>種別ごとに入口を分ける。</strong> 他の入口の検査を緩めて通すと、
     * 守りが何も守らなくなる（{@link #customsCleared} と同じ判断）。
     *
     * <p><strong>コードが無ければ作らせない。</strong> 中身の無い通知を
     * 「伝えた」として残すと、<strong>記録があるのに何を伝えたのか分からない</strong>という
     * 最も困る形になる。確定前の予約にはコードが無い。
     */
    public static BookingNotification claimCodeResent(
            BookingId bookingId, String recipientEmail,
            String claimCode, Instant sentAt, String sentBy) {
        requireRecipient(recipientEmail);
        if (claimCode == null || claimCode.isBlank()) {
            throw new IllegalArgumentException(
                    "引取確認コードが採番されていないため伝えられません");
        }
        return new BookingNotification(null, bookingId, NotificationType.CLAIM_CODE_RESENT,
                recipientEmail,
                "引取確認コードは %s です。引き取りの際に港でご提示ください。".formatted(claimCode),
                NotificationDelivery.succeeded(sentAt, sentBy));
    }

    /**
     * 請求書の発行（US23。<strong>利用者に見せる語は「請求書」に統一する</strong>）。
     *
     * <p><strong>種別ごとに入口を分ける。</strong> 他の入口の検査を緩めて通すと、
     * 守りが何も守らなくなる。
     *
     * <p><strong>金額と支払期限を文面に残す。</strong> 「送った」だけの記録では、
     * 荷主から「いくらの請求書か」と問われたときに答えられない。
     */
    public static BookingNotification invoiceIssued(
            BookingId bookingId, String recipientEmail,
            String invoiceNumber, String amount, String dueDate,
            Instant sentAt, String sentBy) {
        requireRecipient(recipientEmail);
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("精算書番号は必須です");
        }
        return new BookingNotification(null, bookingId, NotificationType.INVOICE_ISSUED,
                recipientEmail,
                "請求書 %s（請求金額 %s 円、支払期限 %s）を発行しました。"
                        .formatted(invoiceNumber, amount, dueDate),
                NotificationDelivery.succeeded(sentAt, sentBy));
    }

    /**
     * 予約キャンセルの承認を伝える（US30）。
     *
     * <p><strong>陸揚げ地を文面に残す。</strong> 荷主にとって「どこで降ろすか」は
     * 引き取りの段取りに直結する。「承認しました」だけでは動けない。
     */
    public static BookingNotification cancellationApproved(
            BookingId bookingId, String recipientEmail, String dischargeUnlocode,
            Instant sentAt, String sentBy) {
        requireRecipient(recipientEmail);
        if (dischargeUnlocode == null || dischargeUnlocode.isBlank()) {
            throw new IllegalArgumentException("陸揚げ地は必須です");
        }
        return new BookingNotification(null, bookingId,
                NotificationType.CANCELLATION_APPROVED, recipientEmail,
                "予約のキャンセルを承認しました。%s で陸揚げします。"
                        .formatted(dischargeUnlocode),
                NotificationDelivery.succeeded(sentAt, sentBy));
    }

    /**
     * 予約キャンセルの却下を伝える（US30）。
     *
     * <p><strong>理由を文面に残す。</strong> 却下されたことだけを伝えると、
     * 荷主は次に何をすればよいか分からない。
     */
    public static BookingNotification cancellationRejected(
            BookingId bookingId, String recipientEmail, String reason,
            Instant sentAt, String sentBy) {
        requireRecipient(recipientEmail);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("却下の理由は必須です");
        }
        return new BookingNotification(null, bookingId,
                NotificationType.CANCELLATION_REJECTED, recipientEmail,
                "予約のキャンセルは承認されませんでした。理由: %s".formatted(reason),
                NotificationDelivery.succeeded(sentAt, sentBy));
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
