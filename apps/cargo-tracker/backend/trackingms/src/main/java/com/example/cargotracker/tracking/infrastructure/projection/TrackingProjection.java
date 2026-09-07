package com.example.cargotracker.tracking.infrastructure.projection;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingSummaryMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.axonframework.messaging.core.annotation.MessageIdentifier;
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
    private final TrackingEventMapper history;
    private final Clock clock;

    public TrackingProjection(TrackingSummaryMapper trackings, TrackingEventMapper history,
            Clock clock) {
        this.trackings = trackings;
        this.history = history;
        this.clock = clock;
    }

    /**
     * 到着予定。<b>予定の旅程の最終区間の荷降し</b>（区間は積む順）。
     *
     * <p><b>導出はここ 1 か所。</b> 所要日数から計算すると、計算式が画面ごとに
     * 増えて違う日付が出る（IT7 引き継ぎ 7）。</p>
     */
    private static java.time.Instant estimatedArrival(
            List<TrackingInitializedEvent.Leg> legs) {
        return legs.isEmpty() ? null : legs.get(legs.size() - 1).unloadTime();
    }

    @EventHandler
    public void on(TrackingInitializedEvent event) {
        var now = clock.instant();
        trackings.insert(new TrackingSummaryMapper.TrackingSummaryRow(
                event.trackingNumber(), event.bookingId(), event.shipperId(),
                event.originUnLocode(), event.destinationUnLocode(), event.cargoType(),
                // 追跡を始めた直後は未受領。**状態はイベントに載って来ない**ので、
                // trackingms が自分の状態機械で決める。
                TransportStatus.NOT_RECEIVED.name(),
                // 始まった時点では、まだどこにも着いていない。
                null,
                // **到着予定は投影が 1 か所で決める**（予定の旅程の最終区間の荷降し）。
                // 一覧のたびに旅程を引くと、1 行ごとの往復が残る。
                estimatedArrival(event.legs()),
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

    /**
     * 状態が変わった（US17 §受入基準 3）。<b>一覧の現在値と履歴の 1 行を対で書く</b>。
     *
     * <p><b>履歴の主キーは元イベントの識別子</b>（{@link MessageIdentifier}）。追記の表なので
     * 採番すると、投影を読み直すたびに同じ内容の行が積み上がる（IT2 で実在した欠陥）。</p>
     *
     * <p><b>直前の行を引かない。</b>「何から何へ」はイベントが持って来る。引くと、
     * 再配送や順序の入れ替わりで壊れる。</p>
     */
    @EventHandler
    public void on(TransportStatusUpdatedEvent event, @MessageIdentifier String eventId) {
        var now = clock.instant();
        var current = trackings.findByTrackingNumber(event.trackingNumber());
        if (current != null) {
            trackings.updateStatus(event.trackingNumber(), event.newStatus().name(),
                    event.occurredAt(), event.location(), now, eventId);
        }
        history.insert(new TrackingEventMapper.TrackingEventRow(eventId, event.trackingNumber(),
                event.source().eventType(),
                event.previousStatus() == null ? null : event.previousStatus().name(),
                event.newStatus().name(), event.location(), event.occurredAt(),
                event.updatedBy(), now));
    }
}
