package com.example.trackingms.application.internal.queryservices;

import com.example.trackingms.application.internal.outboundservices.acl.ShipperCargoSnapshotFinder;
import com.example.trackingms.domain.repository.TrackingActivityRepository;
import com.example.trackingms.application.internal.outboundservices.acl.UserShipperLinkFinder;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** ログインした荷主が、自社貨物だけを追跡する。 */
@Service
public class ShipperTrackingQueryUseCase {

    public static final int LIST_LIMIT = 100;
    public static final int HISTORY_LIMIT = 200;

    private final TrackingActivityRepository activities;
    private final UserShipperLinkFinder links;
    private final ShipperCargoSnapshotFinder snapshots;
    private final ZoneId zone;

    public ShipperTrackingQueryUseCase(TrackingActivityRepository activities,
            UserShipperLinkFinder links, ShipperCargoSnapshotFinder snapshots) {
        this(activities, links, snapshots, ZoneId.of("Asia/Tokyo"));
    }

    @Autowired
    public ShipperTrackingQueryUseCase(TrackingActivityRepository activities,
            UserShipperLinkFinder links, ShipperCargoSnapshotFinder snapshots, ZoneId zone) {
        this.activities = activities;
        this.links = links;
        this.snapshots = snapshots;
        this.zone = zone;
    }

    /**
     * 自社貨物だけを一覧で返す。紐付けが無ければ候補も読まない。
     *
     * <p><strong>先に荷主で絞ってから追跡を引く。</strong>追跡の直近 {@value #LIST_LIMIT} 件を
     * 取ってから荷主で絞ると、貨物が増えた荷主の古い貨物が窓の外に落ちて一覧から消える
     * ——件数だけで壊れ、受入基準は満たしたまま業務が成り立たなくなる。
     */
    public ShipperTrackingQueryResult list(String username) {
        Optional<Long> shipperId = links.findLinkedShipperId(username);
        if (shipperId.isEmpty()) {
            return ShipperTrackingQueryResult.unlinked();
        }
        List<TrackingNumber> owned = snapshots.findByShipperId(shipperId.orElseThrow()).stream()
                .map(ShipperCargoSnapshot::trackingNumber)
                .flatMap(number -> restore(number).stream())
                .toList();
        if (owned.isEmpty()) {
            return ShipperTrackingQueryResult.linked(List.of());
        }
        List<ShipperTrackingSummary> cargos = activities.findByTrackingNumbers(owned).stream()
                .map(ShipperTrackingSummary::from)
                .toList();
        return ShipperTrackingQueryResult.linked(cargos);
    }

    /** 自社貨物なら詳細を返す。他社貨物・未紐付け・形式不正は同じく空にする。 */
    public Optional<ShipperTrackingDetail> detail(String username, String trackingNumber) {
        Optional<Long> shipperId = links.findLinkedShipperId(username);
        Optional<TrackingNumber> restored = restore(trackingNumber);
        if (shipperId.isEmpty() || restored.isEmpty()) {
            return Optional.empty();
        }
        return activities.findByTrackingNumber(restored.orElseThrow())
                .filter(activity -> ownedBy(activity, shipperId.orElseThrow()))
                .map(activity -> ShipperTrackingDetail.from(ShipperTrackingSummary.from(activity),
                        activities.findEvents(activity.trackingNumber(), HISTORY_LIMIT).stream()
                                .map(event -> ShipperTrackingEvent.from(event, zone))
                                .toList()));
    }

    private boolean ownedBy(TrackingActivity activity, Long shipperId) {
        return snapshots.findByTrackingNumber(activity.trackingNumber())
                .map(ShipperCargoSnapshot::shipperId)
                .filter(shipperId::equals)
                .isPresent();
    }

    private static Optional<TrackingNumber> restore(String trackingNumber) {
        try {
            return Optional.of(TrackingNumber.of(trackingNumber));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
