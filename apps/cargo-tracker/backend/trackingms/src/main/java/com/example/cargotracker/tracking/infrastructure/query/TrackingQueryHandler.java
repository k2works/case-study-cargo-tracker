package com.example.cargotracker.tracking.infrastructure.query;

import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingSummaryMapper;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.FindPublicTrackingQuery;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.CountRecentlyChangedQuery;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.FindTrackingQuery;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.RecentlyChangedView;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.FindTrackingsQuery;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.TrackingEventView;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.TrackingListItemView;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.TrackingListView;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.TrackingView;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.PublicTrackingEventView;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.PublicTrackingView;
import java.util.List;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 追跡の問い合わせ。読み取りモデルは投影テーブルだけを見る。 */
@Component
public class TrackingQueryHandler {

    private final TrackingSummaryMapper trackings;
    private final TrackingEventMapper history;

    public TrackingQueryHandler(TrackingSummaryMapper trackings, TrackingEventMapper history) {
        this.trackings = trackings;
        this.history = history;
    }

    /**
     * 公開照会（S44 / US18）。
     *
     * <p><b>見つからないときは {@code null} を返す。</b> 存在しない番号と権限の無い
     * 番号を区別しない（ui_design.md）。区別すると、総当たりで「実在するが自分の
     * ものではない番号」を選り分けられる。</p>
     */
    @QueryHandler
    public PublicTrackingView handle(FindPublicTrackingQuery query) {
        var row = trackings.findByTrackingNumber(normalize(query.trackingNumber()));
        if (row == null) {
            return null;
        }

        var legs = trackings.findLegs(row.trackingNumber());
        List<PublicTrackingEventView> events = history.findHistory(row.trackingNumber()).stream()
                .map(event -> new PublicTrackingEventView(event.occurredAt(),
                        label(event.newStatus()), event.location()))
                .toList();

        return new PublicTrackingView(row.trackingNumber(), row.originUnlocode(),
                row.destinationUnlocode(), label(row.transportStatus()),
                row.currentUnlocode(), departure(legs), row.estimatedArrival(), events);
    }

    /**
     * 入力のゆらぎを吸収する（ui_design.md「入力形式」）。
     *
     * <p>大文字小文字とハイフンの有無を吸収する。<b>形式の検査はしない</b>——
     * 形式違いも「見つかりません」に落ちるので、ここで分岐を増やす意味がない
     * （画面が照会前に赤字で知らせる）。</p>
     */
    private static String normalize(String trackingNumber) {
        if (trackingNumber == null) {
            return null;
        }
        String trimmed = trackingNumber.trim().toUpperCase(java.util.Locale.ROOT);
        return trimmed.startsWith("TRK-") || trimmed.isEmpty() ? trimmed : "TRK-" + trimmed;
    }

    /** 列挙名を利用者に見せない。呼び名は要素表が正典（{@code TransportStatus}）。 */
    private static String label(String status) {
        return TransportStatus.valueOf(status).label();
    }


    /** 出発。予定の旅程の最初の区間の積み込み。 */
    private static java.time.Instant departure(
            List<TrackingSummaryMapper.TrackingLegRow> legs) {
        return legs.isEmpty() ? null : legs.get(0).loadTime();
    }


    /**
     * 追跡一覧（S40）。<b>荷主が指定されていれば自社のぶんだけ</b>（US18）。
     */
    @QueryHandler
    public TrackingListView handle(FindTrackingsQuery query) {
        return new TrackingListView(
                trackings.findAll(query.shipperId(), query.includeDelivered(), query.limit())
                        .stream()
                        // **1 行ごとに旅程と履歴を引かない。** 既定 50 件で 100 回超の
                        // 往復になり、それが 30 秒ごとに繰り返される。投影が写した
                        // 列をそのまま読む（data-model.md の「一覧が JOIN しない」）。
                        .map(row -> new TrackingListItemView(row.trackingNumber(),
                                row.originUnlocode(), row.destinationUnlocode(),
                                label(row.transportStatus()), row.currentUnlocode(),
                                row.estimatedArrival(), row.lastStatusChangedAt()))
                        .toList(),
                trackings.countAll(query.shipperId(), query.includeDelivered()));
    }

    /**
     * 追跡詳細（S41）。<b>荷主が指定されていれば、その荷主のものでなければ返さない</b>。
     *
     * <p><b>他社のものを「見つからない」として返す。</b> 権限が無いことを伝えると、
     * 番号が実在することが分かる（公開照会と同じ判断）。</p>
     */
    @QueryHandler
    public TrackingView handle(FindTrackingQuery query) {
        var row = trackings.findByTrackingNumber(query.trackingNumber());
        if (row == null || !belongsTo(row, query.shipperId())) {
            return null;
        }

        var status = TransportStatus.valueOf(row.transportStatus());
        List<TrackingEventView> events = history.findHistory(row.trackingNumber()).stream()
                .map(event -> new TrackingEventView(event.occurredAt(), event.eventType(),
                        event.previousStatus() == null ? null : label(event.previousStatus()),
                        label(event.newStatus()), event.location(), event.recordedBy()))
                .toList();

        return new TrackingView(row.trackingNumber(), row.bookingId(), row.originUnlocode(),
                row.destinationUnlocode(), row.cargoType(), status.name(), status.label(),
                row.currentUnlocode(), row.estimatedArrival(),
                row.lastStatusChangedAt(), events, nextStatuses(status));
    }

    /**
     * 手で動かせる先（S41 の選択肢）。
     *
     * <p><b>画面が遷移表を持たない。</b> 持つと判定が 2 つになり、集約が断る先を
     * 画面が出してしまう（押してから断られる）。集約と同じ述語をそのまま呼ぶ。</p>
     *
     * <p><b>手で選べない先は出さない</b>（{@link TransportStatus#isSetByHand}）。
     * 誤配は荷役が、例外発生は例外の起票が決める。とくに例外発生は<b>解決の画面が
     * 無い IT では行き止まり</b>になる（例外中は手で動かせない）。</p>
     */
    private static List<String> nextStatuses(TransportStatus status) {
        return TransportStatus.manualTransitionsFrom(status).stream().map(Enum::name).toList();
    }

    private static boolean belongsTo(TrackingSummaryMapper.TrackingSummaryRow row,
            String shipperId) {
        return shipperId == null || shipperId.equals(row.shipperId());
    }


    /** 直近で状態が変わった件数（S02 荷主 / US17 §4 の代わり）。 */
    @QueryHandler
    public RecentlyChangedView handle(CountRecentlyChangedQuery query) {
        var since = java.time.Instant.now().minus(
                java.time.Duration.ofHours(query.withinHours()));
        return new RecentlyChangedView(
                trackings.countRecentlyChanged(query.shipperId(), since), query.withinHours());
    }
}
