package com.example.cargotracker.booking.interfaces.events;

import com.example.cargotracker.booking.application.internal.commandservices
        .RevertDeliveryCommandService;
import com.example.cargotracker.shared.domain.event.ClaimCancelledEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 引取の取り消しを予約に反映する（US36）。
 *
 * <p><strong>配送完了のまま残すと、届いていない貨物が届いたことになる。</strong>
 * 精算（US23 / Release 2.0）の対象にもなり、請求まで進む。
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。
 */
@Component
public class BookingClaimCancelledEventHandler {

    /** 購読者の名前。メトリクスのタグになる（運用手順書が参照する）。 */
    private static final String SUBSCRIBER = "booking-claim-cancelled";

    private final RevertDeliveryCommandService revertService;
    private final EventualConsistencySkips skips;

    public BookingClaimCancelledEventHandler(
            RevertDeliveryCommandService revertService, EventualConsistencySkips skips) {
        this.revertService = revertService;
        this.skips = skips;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ClaimCancelledEvent event) {
        if (!revertService.revert(event.bookingId(), event.approvedBy())) {
            skips.recordSkip(SUBSCRIBER, "NOT_REVERTED", event.trackingNumber());
        }
    }
}
