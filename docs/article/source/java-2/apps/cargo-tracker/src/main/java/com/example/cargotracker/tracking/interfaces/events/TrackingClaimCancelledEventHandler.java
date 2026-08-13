package com.example.cargotracker.tracking.interfaces.events;

import com.example.cargotracker.shared.domain.event.ClaimCancelledEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import com.example.cargotracker.tracking.application.internal.commandservices
        .CancelClaimCommandService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 引取の取り消しを追跡に反映する（US36）。
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。コミット前に動くと、
 * 承認が巻き戻ったときに<strong>追跡だけが引取前に戻る</strong>。
 *
 * <p><strong>Handling から Tracking を呼ばない</strong>（ADR-012）。運ばれるのは
 * 「取り消しが承認された」という事実であり、状態をどう戻すかは Tracking が決める。
 */
@Component
public class TrackingClaimCancelledEventHandler {

    /** 購読者の名前。メトリクスのタグになる（運用手順書が参照する）。 */
    private static final String SUBSCRIBER = "tracking-claim-cancelled";

    private final CancelClaimCommandService cancelService;
    private final EventualConsistencySkips skips;

    public TrackingClaimCancelledEventHandler(
            CancelClaimCommandService cancelService, EventualConsistencySkips skips) {
        this.cancelService = cancelService;
        this.skips = skips;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ClaimCancelledEvent event) {
        if (!cancelService.cancelClaim(event.trackingNumber())) {
            // **取りこぼしを数える。** 結果整合では利用者の画面に返せないため、
            // ここが唯一「戻らなかった」ことを知る手段になる
            skips.recordSkip(SUBSCRIBER, "NOT_REVERTED", event.trackingNumber());
        }
    }
}
