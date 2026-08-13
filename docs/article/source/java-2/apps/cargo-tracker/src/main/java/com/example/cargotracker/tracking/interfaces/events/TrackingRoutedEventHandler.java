package com.example.cargotracker.tracking.interfaces.events;

import com.example.cargotracker.shared.domain.event.CargoRoutedEvent;
import com.example.cargotracker.shared.infrastructure.observability.EventualConsistencySkips;
import com.example.cargotracker.tracking.application.internal.commandservices.RerouteTrackingCommandService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 経路の割り当てを追跡に反映する（ADR-012）。
 *
 * <p>追跡は目的地と推定到着日の<strong>写し</strong>を持つ。発行時に受け取ったきりだと、
 * 予約が経路を変えても古い到着予定が残り続ける。
 * <strong>写しを持つ判断と、追随する手段は 1 組である。</strong>
 *
 * <p><strong>AFTER_COMMIT で受ける</strong>（ADR-009）。コミット前に動くと、
 * 経路の割り当てが巻き戻ったときに追跡だけが新しい目的地を持つ。
 *
 * <p><strong>追跡番号がまだ無い予約は取りこぼしではない。</strong> 経路の割り当ては
 * 追跡番号の発行より前に起きる。その場合の目的地は発行時に渡される。
 */
@Component
public class TrackingRoutedEventHandler {

    /** 購読者の名前。メトリクスのタグになる（運用手順書が参照する）。 */
    private static final String SUBSCRIBER = "tracking-reroute";

    private final RerouteTrackingCommandService rerouteService;
    private final EventualConsistencySkips skips;

    public TrackingRoutedEventHandler(
            RerouteTrackingCommandService rerouteService, EventualConsistencySkips skips) {
        this.rerouteService = rerouteService;
        this.skips = skips;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CargoRoutedEvent event) {
        var result = rerouteService.reroute(
                event.bookingId(), event.destinationUnlocode(), event.estimatedArrivalDate());

        switch (result) {
            // **追跡がまだ無いのは正常である**（発行前の経路割り当て）。数えない
            case NOT_FOUND -> { /* 発行時に渡されるため取りこぼしではない */ }
            case CONFLICTED -> skips.recordSkip(
                    SUBSCRIBER, result.name(), event.bookingId().toString());
            default -> { /* 反映できた */ }
        }
    }
}
