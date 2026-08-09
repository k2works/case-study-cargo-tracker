package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingNotification;
import com.example.cargotracker.booking.domain.model.NotificationType;
import com.example.cargotracker.booking.domain.repository.BookingNotificationRepository;
import com.example.cargotracker.shared.domain.event.CargoExceptionRaisedEvent;
import com.example.cargotracker.shared.domain.event.CargoExceptionResolvedEvent;
import com.example.cargotracker.shared.domain.event.CustomsStatusChangedEvent;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 例外の発生・対応報告を荷主への通知として記録する（US19 / US20）。
 *
 * <p><strong>文面は Booking が組み立てる。</strong> イベントが運ぶのは起きた事実だけで、
 * 荷主に何と伝えるかは通知を持つ側が決める（ADR-009）。
 *
 * <p>ADR-006 により外部へは送らない。予約詳細の通知履歴に現れることをもって
 * 「知らせた」とする（US12 で確立した扱い）。
 */
@Service
public class RecordExceptionNotificationCommandService {

    /** 反映の結果。**呼び出し側が数えるために返す**（ADR-009）。 */
    public enum Result {
        /** 記録した。 */
        RECORDED,
        /** 予約が見つからない。 */
        NOT_FOUND,
        /** 宛先が無い。**宛先の無い通知を「知らせた」として残さない。** */
        NO_RECIPIENT
    }

    private final BookingQueryService queryService;
    private final BookingNotificationRepository repository;
    private final Clock clock;

    public RecordExceptionNotificationCommandService(
            BookingQueryService queryService,
            BookingNotificationRepository repository,
            Clock clock) {
        this.queryService = queryService;
        this.repository = repository;
        this.clock = clock;
    }

    /** 例外が起きたことを知らせる（受入基準「荷主に発生の通知が送信される」）。 */
    @Transactional
    public Result recordRaised(CargoExceptionRaisedEvent event) {
        // **文字列連結で組み立てる。** 記録に残す文面にプラットフォーム依存の
        // 改行（%n）を入れる理由は無い（RecordStatusNotificationCommandService と同じ判断）
        String message = "貨物に例外が発生しました。\n"
                + "追跡番号: " + event.trackingNumber() + "\n"
                + "例外種別: " + event.exceptionTypeLabel() + "\n"
                + "発生場所: " + event.locationUnlocode() + "\n"
                + "発生日時: " + event.occurredAt() + "\n"
                + "状況: " + (event.description() == null ? "" : event.description()) + "\n"
                + "対応が決まりしだい、あらためてご連絡します。\n";
        return save(event.bookingId(), NotificationType.EXCEPTION_RAISED,
                message, event.raisedBy());
    }

    /**
     * 対応が済んだことを知らせる（受入基準「対応報告を送信できる」）。
     *
     * <p><strong>復帰した状態も伝える。</strong> 「解決しました」だけでは、
     * 荷主は自分の貨物がいまどこにあるのか分からない。
     */
    @Transactional
    public Result recordResolved(CargoExceptionResolvedEvent event) {
        String message = "貨物の例外に対応しました。\n"
                + "追跡番号: " + event.trackingNumber() + "\n"
                + "例外種別: " + event.exceptionTypeLabel() + "\n"
                + "対応日時: " + event.resolvedAt() + "\n"
                + "対応内容: "
                + (event.resolutionNotes() == null ? "" : event.resolutionNotes()) + "\n"
                + "現在の状態: " + event.statusAfterLabel() + "\n";
        return save(event.bookingId(), NotificationType.EXCEPTION_RESOLVED,
                message, event.resolvedBy());
    }

    /**
     * 通関が下りたことを知らせる（US29「荷主・荷受人に通関完了が通知される」）。
     *
     * <p><strong>次に何が起きるのかまで書く。</strong> 荷主が待っているのは
     * 「引き取れるようになったか」であり、「通関しました」だけでは
     * それが分からない。
     */
    @Transactional
    public Result recordCustomsCleared(CustomsStatusChangedEvent event) {
        String message = "通関手続きが完了しました。\n"
                + "追跡番号: " + event.trackingNumber() + "\n"
                + "申告番号: " + event.declarationNumber() + "\n"
                + "完了日時: " + event.changedAt() + "\n"
                + "引き取りの手続きに進めます。\n";
        var booking = queryService.findById(event.bookingId().toString());
        if (booking.isEmpty()) {
            return Result.NOT_FOUND;
        }
        String recipient = booking.get().shipperEmail();
        if (recipient == null || recipient.isBlank()) {
            return Result.NO_RECIPIENT;
        }
        repository.save(BookingNotification.customsCleared(
                new BookingId(event.bookingId()), recipient, message, clock.instant(),
                event.changedBy()));

        // **受入基準は「荷主・荷受人に」と書いている**（US29）。荷受人は引き取りに
        // 来る当人であり、通関が下りたことを最も待っている。
        // **連絡先が未登録なら送れない** — 荷受人は予約の時点では未確定でありうる
        // （US16）。その場合も荷主への記録は残すため、ここでは失敗にしない
        String consignee = booking.get().consigneeEmail();
        if (consignee != null && !consignee.isBlank() && !consignee.equals(recipient)) {
            repository.save(BookingNotification.customsCleared(
                    new BookingId(event.bookingId()), consignee, message, clock.instant(),
                    event.changedBy()));
        }
        return Result.RECORDED;
    }

    /**
     * 通知を 1 件積む。
     *
     * <p><strong>{@code record} という名前にしない。</strong> Java の制限識別子であり、
     * IT7 で {@code EventualConsistencySkips} が同じ罠を踏んでいる（{@code recordSkip} に改名済み）。
     */
    private Result save(UUID bookingId, NotificationType type, String message, String by) {
        var booking = queryService.findById(bookingId.toString());
        if (booking.isEmpty()) {
            return Result.NOT_FOUND;
        }
        String recipient = booking.get().shipperEmail();
        if (recipient == null || recipient.isBlank()) {
            return Result.NO_RECIPIENT;
        }
        repository.save(BookingNotification.exception(
                new BookingId(bookingId), type, recipient, message, clock.instant(), by));
        return Result.RECORDED;
    }
}
