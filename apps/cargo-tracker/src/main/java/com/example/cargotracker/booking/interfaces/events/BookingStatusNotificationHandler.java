package com.example.cargotracker.booking.interfaces.events;

import com.example.cargotracker.booking.application.internal.commandservices
        .RecordStatusNotificationCommandService;
import com.example.cargotracker.shared.domain.event.CargoStatusUpdatedEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 貨物状態の手動更新を荷主への通知として記録する（US17 の受入基準）。
 *
 * <p>ADR-006 により外部へは送らない。<strong>通知の実体は記録</strong>であり（US12）、
 * 予約詳細の通知履歴に現れることをもって「知らせた」とする。
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。コミット前に動くと、
 * 状態の更新が巻き戻ったときに「知らせた」記録だけが残る。
 */
@Component
public class BookingStatusNotificationHandler {

    /** 購読者の名前。メトリクスのタグになる（運用手順書が参照する）。 */
    private static final String SUBSCRIBER = "booking-status-notification";

    private final RecordStatusNotificationCommandService recordService;
    private final EventualConsistencySkips skips;

    public BookingStatusNotificationHandler(
            RecordStatusNotificationCommandService recordService,
            EventualConsistencySkips skips) {
        this.recordService = recordService;
        this.skips = skips;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CargoStatusUpdatedEvent event) {
        var result = recordService.record(event);
        if (result != RecordStatusNotificationCommandService.Result.RECORDED) {
            // **取りこぼしを数える。** 結果整合では利用者の画面に返せないため、
            // ここが唯一「知らせられなかった」ことを知る手段になる
            skips.recordSkip(SUBSCRIBER, result.name(), event.trackingNumber());
        }
    }
}
