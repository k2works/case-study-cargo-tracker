package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingNotification;
import com.example.cargotracker.booking.domain.repository.BookingNotificationRepository;
import com.example.cargotracker.shared.domain.event.CargoStatusUpdatedEvent;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 貨物状態の更新を荷主への通知として記録する（US17 の受入基準）。
 *
 * <p><strong>文面は Booking が組み立てる。</strong> イベントが運ぶのは起きた事実だけであり、
 * それを荷主に何と伝えるかは通知を持つ側（Booking）が決める（ADR-009）。
 */
@Service
public class RecordStatusNotificationCommandService {

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

    public RecordStatusNotificationCommandService(
            BookingQueryService queryService,
            BookingNotificationRepository repository,
            Clock clock) {
        this.queryService = queryService;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Result record(CargoStatusUpdatedEvent event) {
        var booking = queryService.findById(event.bookingId().toString());
        if (booking.isEmpty()) {
            return Result.NOT_FOUND;
        }
        String recipient = booking.get().shipperEmail();
        if (recipient == null || recipient.isBlank()) {
            return Result.NO_RECIPIENT;
        }

        repository.save(BookingNotification.statusUpdated(
                new BookingId(event.bookingId()),
                recipient,
                message(event),
                clock.instant(),
                event.updatedBy()));
        return Result.RECORDED;
    }

    /**
     * 荷主に伝える文面。
     *
     * <p><strong>何が起きたかを書く。</strong> 「状態が変わりました」だけでは、
     * 荷主は自分の貨物に何が起きたのか分からない。
     */
    private static String message(CargoStatusUpdatedEvent event) {
        // **文字列連結で組み立てる。** テキストブロック + formatted にすると SpotBugs が
        // 改行に %n を使うよう促すが、記録に残す文面にプラットフォーム依存の改行を
        // 入れる理由は無い（レートリミットの応答本文と同じ判断）
        return "貨物の状態が更新されました。\n"
                + "追跡番号: " + event.trackingNumber() + "\n"
                + "状態: " + event.transportStatusLabel() + "\n"
                + "発生場所: " + event.locationUnlocode() + "\n"
                + "発生日時: " + event.occurredAt() + "\n";
    }
}
