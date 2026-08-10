package com.example.cargotracker.booking.interfaces.events;

import com.example.cargotracker.booking.application.internal.commandservices
        .SyncItineraryScheduleCommandService;
import com.example.cargotracker.shared.domain.event.VoyageRescheduledEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 航海の更新を区間の「いまの日程」に写す（US25 / IT12 持ち越し C3）。
 *
 * <p><strong>写しが無いと、予約詳細は Routing のテーブルを JOIN するしかない。</strong>
 * ADR-015 の許容リストに「次に返す候補」として 2 行を残していたのはそのためである。
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。航海の更新が確定していない
 * 段階で写すと、更新が巻き戻ったときに予約側だけ新しい日程が残る。
 *
 * <p><strong>0 件でも記録しない。</strong> その便を使う予約が無いのは日常であり、
 * 異常として数えると「常に起きている異常」になって読み飛ばされる
 * （{@code EventualConsistencySkips} は取りこぼしを数えるためのものである）。
 */
@Component
public class BookingVoyageRescheduledEventHandler {

    private final SyncItineraryScheduleCommandService syncService;

    public BookingVoyageRescheduledEventHandler(SyncItineraryScheduleCommandService syncService) {
        this.syncService = syncService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(VoyageRescheduledEvent event) {
        syncService.sync(event);
    }
}
