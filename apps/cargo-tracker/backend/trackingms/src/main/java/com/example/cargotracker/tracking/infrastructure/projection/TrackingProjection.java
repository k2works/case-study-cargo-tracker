package com.example.cargotracker.tracking.infrastructure.projection;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.tracking.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.tracking.domain.model.events.CargoMisroutedEvent;
import com.example.cargotracker.tracking.domain.model.events.ExceptionEscalatedEvent;
import com.example.cargotracker.tracking.domain.model.events.ExceptionResponseStartedEvent;
import com.example.cargotracker.tracking.domain.model.events.ExceptionShipperNotifiedEvent;
import com.example.cargotracker.tracking.domain.model.events.HandlingDeferredEvent;
import com.example.cargotracker.tracking.domain.model.events.HandlingNotAppliedEvent;
import com.example.cargotracker.tracking.domain.model.events.TrackingExceptionRegisteredEvent;
import com.example.cargotracker.tracking.domain.model.events.TrackingExceptionResolvedEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.ResponseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.StatusUpdateSource;
import com.example.cargotracker.tracking.infrastructure.persistence.ExceptionNotificationMapper;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingExceptionMapper;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingSummaryMapper;
import java.time.Clock;
import java.time.Instant;
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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TrackingProjection.class);

    private final TrackingSummaryMapper trackings;
    private final TrackingEventMapper history;
    private final TrackingExceptionMapper exceptions;
    private final ExceptionNotificationMapper notifications;
    private final Clock clock;

    public TrackingProjection(TrackingSummaryMapper trackings, TrackingEventMapper history,
            TrackingExceptionMapper exceptions, ExceptionNotificationMapper notifications,
            Clock clock) {
        this.trackings = trackings;
        this.history = history;
        this.exceptions = exceptions;
        this.notifications = notifications;
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
                // 例外はまだ無い。件数は起票のたびに明細から数え直す。
                // 誤配はまだ無い（US28）。予定外の荷役が来たときだけ立つ。
                event.initializedAt(), event.initializedAt(), now, null, 0, 0, null, false));

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

    /**
     * 例外が起票された（US19 §受入基準 1・5）。
     *
     * <p><b>{@code urgent} は写すだけ</b>（不変条件 7）。判定を投影に書き直すと、
     * 種別が増えたときに片方だけ直る。</p>
     */
    @EventHandler
    public void on(TrackingExceptionRegisteredEvent event, @MessageIdentifier String eventId) {
        var now = clock.instant();
        var current = trackings.findByTrackingNumber(event.trackingNumber());
        exceptions.insert(new TrackingExceptionMapper.TrackingExceptionRow(
                event.exceptionId(), event.trackingNumber(), event.exceptionType(),
                ResponseStatus.REPORTED.name(), event.urgent(), event.unLocode(),
                // 起票の時点では対応内容も新しい期限も無い。対応開始が書き足す。
                event.description(), null, null, null,
                // **予約番号は追跡の投影から引く**（IT10 レビュー N9）。契約イベントを
                // 変えずに済み、写す先は 1 つ。escalation の時刻は起票の時点では無い。
                current == null ? null : current.bookingId(), null,
                event.occurredAt(), null, now));
        refreshCounts(event.trackingNumber(),
                statusBeforeException(current, event), now);
        // **起票と解決は逆向きの出来事。** 同じ印にすると履歴が読めない。
        writeHistory(new HistoryEntry(eventId, event.trackingNumber(),
                StatusUpdateSource.EXCEPTION.eventType(), event.statusBeforeException(),
                TransportStatus.EXCEPTION, event.unLocode(), event.occurredAt(),
                event.reportedBy()), now);
    }

    /**
     * 緊急の例外を上位者へ知らせた（US20 §受入基準 3 / IT11）。
     *
     * <p><b>記録と読み口は対で出す。</b> 例外一覧（S42）が「いつ知らせたか」を
     * 出せて初めて、管理者は自分が見るべきものを見つけられる。</p>
     */
    @EventHandler
    public void on(ExceptionEscalatedEvent event) {
        int updated = exceptions.markEscalated(event.exceptionId(), event.escalatedAt(),
                clock.instant());
        if (updated == 0) {
            log.warn("escalation を書ける例外が投影に無い: exceptionId={}", event.exceptionId());
        }
    }

    /** 例外への対応が始まった（US19 §受入基準 4）。 */
    @EventHandler
    public void on(ExceptionResponseStartedEvent event) {
        var now = clock.instant();
        // **入力した値を落とさない。** 新しい到着予定日は一覧の並びに効く。
        int updated = exceptions.updateResponseStatus(event.exceptionId(),
                ResponseStatus.RESPONDING.name(), event.newEstimatedArrival(),
                event.plan(), now);
        if (updated == 0) {
            log.warn("対応開始を書ける例外が投影に無い: exceptionId={}", event.exceptionId());
        }
    }

    /**
     * 例外が解決した（US19 §受入基準 4・5）。
     *
     * <p><b>行は消さない</b>（不変条件 6）。起票の内容は残り、料金調整の根拠になる。
     * 減るのは未解決の件数だけである。</p>
     */
    @EventHandler
    public void on(TrackingExceptionResolvedEvent event, @MessageIdentifier String eventId) {
        var now = clock.instant();
        int updated = exceptions.resolve(event.exceptionId(), ResponseStatus.RESOLVED.name(),
                event.resolution(), event.resolvedAt(), now);
        if (updated == 0) {
            log.warn("解決を書ける例外が投影に無い: exceptionId={}", event.exceptionId());
        }
        var current = trackings.findByTrackingNumber(event.trackingNumber());
        refreshCounts(event.trackingNumber(),
                current == null ? null : current.statusBeforeException(), now);
        writeHistory(new HistoryEntry(eventId, event.trackingNumber(),
                StatusUpdateSource.RESOLVED.eventType(), TransportStatus.EXCEPTION, null, null,
                event.resolvedAt(), event.resolvedBy()), now);
    }

    /**
     * 荷主へ知らせた（US19 §受入基準 3）。
     *
     * <p><b>記録と読み口は対で出す。</b> Event Store に積むだけでは誰も読めず、
     * 「記録で満たす」という受入基準の満たし方そのものが成り立たない
     * （IT10 のレビューで実測）。</p>
     */
    @EventHandler
    public void on(ExceptionShipperNotifiedEvent event, @MessageIdentifier String eventId) {
        notifications.insert(new ExceptionNotificationMapper.ExceptionNotificationRow(
                eventId, event.trackingNumber(), event.exceptionId(), event.means(),
                event.summary(), event.notifiedBy(), event.notifiedAt(), clock.instant()));
    }

    /**
     * 届いたが反映できなかった荷役（IT9 レビュー M6）。
     *
     * <p><b>状態は動かさない。</b> 履歴に残すだけである——「荷役は記録したのに
     * 追跡が動いていない」という問い合わせに、これが無いと答えられない。</p>
     */
    @EventHandler
    public void on(HandlingNotAppliedEvent event, @MessageIdentifier String eventId) {
        writeHistory(new HistoryEntry(eventId, event.trackingNumber(), "NOT_APPLIED",
                event.currentStatus(), event.attemptedStatus(), event.unLocode(),
                event.completedAt(), null), clock.instant());
    }

    /**
     * 誤配を検知した（US28 §受入基準 2・3）。
     *
     * <p><b>状態から導かない。</b> 例外の対応中は状態が {@code EXCEPTION} へ
     * 退避するが、誤配であることは変わらない。導くと、バナーが例外の起票と
     * 同時に消える——画面から「なぜ組み直すのか」が読めなくなる。</p>
     */
    @EventHandler
    public void on(CargoMisroutedEvent event, @MessageIdentifier String eventId) {
        int updated = trackings.markMisrouted(event.trackingNumber(), event.unLocode(),
                clock.instant());
        if (updated == 0) {
            log.warn("誤配を書ける追跡が投影に無い: trackingNumber={}", event.trackingNumber());
        }
        // **検知した荷役を履歴に残す**（US28 §受入基準 3）。バナーは
        // 「いつ・どこで予定外の荷役が記録されたか」を出す。
        writeHistory(new HistoryEntry(eventId, event.trackingNumber(), "MISROUTE",
                null, TransportStatus.MISROUTED, event.unLocode(), event.detectedAt(), null),
                clock.instant());
    }

    /**
     * 例外の対応中に預かった荷役（IT11 引き継ぎ枠 B）。
     *
     * <p><b>「反映できなかった」とは別の行にする。</b> 預かりは<b>あとで適用される</b>
     * ので、追跡管理者にとって次の行動が違う——反映できなかった荷役は荷役側に
     * 問い合わせるが、預かった荷役は例外を解決すれば自然に反映される。同じ
     * 「NOT_APPLIED」で出すと、解決すれば済むものに人が動く。</p>
     */
    @EventHandler
    public void on(HandlingDeferredEvent event, @MessageIdentifier String eventId) {
        writeHistory(new HistoryEntry(eventId, event.trackingNumber(), "DEFERRED",
                event.currentStatus(), event.attemptedStatus(), event.unLocode(),
                event.completedAt(), event.operator()), clock.instant());
    }

    /**
     * 解決したら戻る先（IT10 レビュー 高）。
     *
     * <p><b>2 件目以降の起票では上書きしない。</b> 2 件目は例外発生から起票される
     * ので、そのまま書くと画面の「解決すると何に戻るか」が「例外発生」と出る。
     * 集約と同じ判断（最初の起票の時点を覚える）。</p>
     */
    private static String statusBeforeException(TrackingSummaryMapper.TrackingSummaryRow current,
            TrackingExceptionRegisteredEvent event) {
        if (current != null && current.statusBeforeException() != null) {
            return current.statusBeforeException();
        }
        return event.statusBeforeException() == null
                ? null : event.statusBeforeException().name();
    }

    /** 例外の件数を明細から数え直す（足し引きしない）。 */
    private void refreshCounts(String trackingNumber, String statusBeforeException, Instant now) {
        int updated = trackings.refreshExceptionCounts(trackingNumber, statusBeforeException, now);
        if (updated == 0) {
            log.warn("例外の件数を書ける追跡が投影に無い: trackingNumber={}", trackingNumber);
        }
    }

    /**
     * 履歴の 1 行（追記系。主キーは元イベントの識別子）。
     *
     * <p>引数を並べる代わりに record で受ける。9 個の引数を並べると、呼ぶ側が
     * 順序を取り違えても型が同じところは気づけない。</p>
     */
    private record HistoryEntry(
            String eventId,
            String trackingNumber,
            String eventType,
            TransportStatus previous,
            TransportStatus next,
            String location,
            Instant occurredAt,
            String recordedBy) {

        /**
         * 履歴に書く「変わった先」。
         *
         * <p>{@code new_status} は NOT NULL。<b>動かないものは「今の状態」を書く</b>
         * ——反映できなかった荷役（M6）や解決の記録は、状態そのものを動かさない。</p>
         */
        String newStatusName() {
            if (next != null) {
                return next.name();
            }
            return previous == null ? TransportStatus.EXCEPTION.name() : previous.name();
        }
    }

    /** 履歴を 1 行足す。 */
    private void writeHistory(HistoryEntry entry, Instant now) {
        history.insert(new TrackingEventMapper.TrackingEventRow(entry.eventId(),
                entry.trackingNumber(), entry.eventType(),
                entry.previous() == null ? null : entry.previous().name(),
                entry.newStatusName(), entry.location(), entry.occurredAt(),
                entry.recordedBy(), now));
    }
}
