package com.example.trackingms.application.internal.queryservices;

import com.example.trackingms.application.internal.outboundservices.acl.ShipperCargoSnapshotFinder;
import com.example.trackingms.application.internal.outboundservices.acl.UserShipperLinkFinder;
import com.example.trackingms.domain.model.valueobjects.NoticeWatermark;
import com.example.trackingms.domain.model.valueobjects.ShipperNotice;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.repository.NoticeWatermarkRepository;
import com.example.trackingms.domain.repository.ShipperNoticeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * ログインした荷主に、まだ見ていない知らせを届ける（US39）。
 *
 * <p><strong>自社の貨物かどうかの判定は、一覧（US33）と同じ道を通る</strong>
 * ——利用者と荷主の紐付けを引き、その荷主の貨物に絞る。ここで別の絞り込みを書くと、
 * 片方だけが正しくなり、もう片方が他社の知らせを漏らす。
 */
@Service
public class ShipperNoticeQueryUseCase {

    /**
     * 一度に返す件数の上限。
     *
     * <p><strong>ポップアップは積み上がる。</strong>長く画面を閉じていた荷主に 100 件を
     * 一度に出すと、画面が知らせで埋まって業務が止まる。あふれた分は一覧
     * （自社の貨物 → 詳細のお知らせ）で読む。
     */
    public static final int UNREAD_LIMIT = 20;

    private final ShipperNoticeRepository notices;
    private final NoticeWatermarkRepository watermarks;
    private final UserShipperLinkFinder links;
    private final ShipperCargoSnapshotFinder snapshots;

    public ShipperNoticeQueryUseCase(ShipperNoticeRepository notices,
            NoticeWatermarkRepository watermarks, UserShipperLinkFinder links,
            ShipperCargoSnapshotFinder snapshots) {
        this.notices = notices;
        this.watermarks = watermarks;
        this.links = links;
        this.snapshots = snapshots;
    }

    /** まだ見ていない知らせを古い順に返す。紐付けが無ければ空。 */
    public List<ShipperNotice> unread(String username) {
        List<TrackingNumber> owned = ownedTrackingNumbers(username);
        if (owned.isEmpty()) {
            return List.of();
        }
        return notices.findNewerThan(owned, watermarks.find(username).lastNoticeId(),
                UNREAD_LIMIT);
    }

    /**
     * そこまで読んだことにする。
     *
     * <p><strong>戻せない</strong>（{@link NoticeWatermark#advanceTo}）。画面を 2 つ開いて
     * いるとき、古い方の応答が後に届くだけで知らせが蘇るのを防ぐ。
     */
    public void acknowledge(String username, long lastNoticeId) {
        watermarks.save(username, watermarks.find(username).advanceTo(lastNoticeId));
    }

    private List<TrackingNumber> ownedTrackingNumbers(String username) {
        Optional<Long> shipperId = links.findLinkedShipperId(username);
        if (shipperId.isEmpty()) {
            return List.of();
        }
        return snapshots.findByShipperId(shipperId.orElseThrow()).stream()
                .map(ShipperCargoSnapshot::trackingNumber)
                .flatMap(number -> restore(number).stream())
                .toList();
    }

    private static Optional<TrackingNumber> restore(String trackingNumber) {
        try {
            return Optional.of(TrackingNumber.of(trackingNumber));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
