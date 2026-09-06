package com.example.cargotracker.tracking.infrastructure.projection;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingSummaryMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 追跡の投影（US14）。
 *
 * <p><b>リプレイで行が増えない形にする。</b> 追跡番号が主キーなので上書きになり、
 * 旅程は先に消してから入れ直す（IT6 の「追記専用の行はリプレイで増える」）。</p>
 *
 * <p><b>Reaction Handler と同じ Group にしない。</b> 投影のリプレイでコマンドが
 * 再送されると、追跡が作り直される（ADR-0001 決定 6）。パッケージで分ける。</p>
 */
@Component
public class TrackingProjection {

    private final TrackingSummaryMapper trackings;
    private final Clock clock;

    public TrackingProjection(TrackingSummaryMapper trackings, Clock clock) {
        this.trackings = trackings;
        this.clock = clock;
    }

    @EventHandler
    public void on(TrackingInitializedEvent event) {
        var now = clock.instant();
        trackings.insert(new TrackingSummaryMapper.TrackingSummaryRow(
                event.trackingNumber(), event.bookingId(),
                event.originUnLocode(), event.destinationUnLocode(), event.cargoType(),
                // 追跡を始めた直後は未受領。**状態はイベントに載って来ない**ので、
                // trackingms が自分の状態機械で決める。
                TransportStatus.NOT_RECEIVED.name(),
                event.initializedAt(), event.initializedAt(), now, null));

        // 旅程は消してから入れ直す。追記だけにすると、リプレイで区間が倍になる。
        trackings.deleteLegs(event.trackingNumber());
        if (event.legs().isEmpty()) {
            return;
        }
        List<TrackingSummaryMapper.TrackingLegRow> rows = new ArrayList<>();
        for (int i = 0; i < event.legs().size(); i++) {
            var leg = event.legs().get(i);
            rows.add(new TrackingSummaryMapper.TrackingLegRow(event.trackingNumber(), i + 1,
                    leg.voyageNumber(), leg.loadUnLocode(), leg.unloadUnLocode(),
                    leg.loadTime(), leg.unloadTime()));
        }
        trackings.insertLegs(event.trackingNumber(), rows);
    }
}
