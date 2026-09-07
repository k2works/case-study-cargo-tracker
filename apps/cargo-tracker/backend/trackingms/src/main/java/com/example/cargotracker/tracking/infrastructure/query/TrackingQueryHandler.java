package com.example.cargotracker.tracking.infrastructure.query;

import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.tracking.infrastructure.persistence.TrackingSummaryMapper;
import com.example.cargotracker.tracking.infrastructure.query.TrackingQueries.FindPublicTrackingQuery;
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
                currentLocation(events), departure(legs), estimatedArrival(legs), events);
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

    /** いまどこか。<b>手動更新で入る分だけ</b>（荷役由来の位置は US15・IT9）。 */
    private static String currentLocation(List<PublicTrackingEventView> events) {
        return events.isEmpty() ? null : events.get(events.size() - 1).location();
    }

    /** 出発。予定の旅程の最初の区間の積み込み。 */
    private static java.time.Instant departure(
            List<TrackingSummaryMapper.TrackingLegRow> legs) {
        return legs.isEmpty() ? null : legs.get(0).loadTime();
    }

    /**
     * 到着予定。<b>予定の旅程の最終区間の荷降し</b>。
     *
     * <p><b>所要日数から計算しない。</b> 計算式が 2 か所にあると、片方だけ直った
     * ときに画面ごとに違う日付が出る（IT7 引き継ぎ 7）。</p>
     */
    private static java.time.Instant estimatedArrival(
            List<TrackingSummaryMapper.TrackingLegRow> legs) {
        return legs.isEmpty() ? null : legs.get(legs.size() - 1).unloadTime();
    }
}
