package com.example.cargotracker.booking.interfaces.events;

import com.example.cargotracker.booking.application.internal.commandservices
        .RecordExceptionNotificationCommandService;
import com.example.cargotracker.shared.domain.event.CargoExceptionRaisedEvent;
import com.example.cargotracker.shared.domain.event.CargoExceptionResolvedEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 例外の発生・対応報告を荷主への通知として記録する（US19 / US20）。
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。コミット前に動くと、
 * 例外の起票が巻き戻ったときに「知らせた」記録だけが残る。
 * 荷主に「遅延しています」と伝えたのに、システムには何も起きていない状態になる。
 */
@Component
public class BookingExceptionNotificationHandler {

    /** 購読者の名前。メトリクスのタグになる（運用手順書が参照する）。 */
    private static final String SUBSCRIBER = "booking-exception-notification";

    private final RecordExceptionNotificationCommandService recordService;
    private final EventualConsistencySkips skips;

    public BookingExceptionNotificationHandler(
            RecordExceptionNotificationCommandService recordService,
            EventualConsistencySkips skips) {
        this.recordService = recordService;
        this.skips = skips;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRaised(CargoExceptionRaisedEvent event) {
        var result = recordService.recordRaised(event);
        if (result != RecordExceptionNotificationCommandService.Result.RECORDED) {
            // **取りこぼしを数える。** 結果整合では利用者の画面に返せないため、
            // ここが唯一「知らせられなかった」ことを知る手段になる
            skips.recordSkip(SUBSCRIBER, result.name(), event.trackingNumber());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResolved(CargoExceptionResolvedEvent event) {
        var result = recordService.recordResolved(event);
        if (result != RecordExceptionNotificationCommandService.Result.RECORDED) {
            skips.recordSkip(SUBSCRIBER, result.name(), event.trackingNumber());
        }
    }
}
