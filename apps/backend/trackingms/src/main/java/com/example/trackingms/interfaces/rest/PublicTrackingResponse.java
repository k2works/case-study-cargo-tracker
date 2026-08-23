package com.example.trackingms.interfaces.rest;

import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingEvent;
import com.example.trackingms.domain.model.TrackingNotice;
import java.time.LocalDate;
import java.util.List;

/**
 * 公開の追跡照会が返すもの（US18・[ADR-024] 決定 5）。
 *
 * <p><strong>返さないものを型に持たない。</strong>予約番号・荷主名・荷受人名・作業者・
 * 航海番号・例外の詳細（{@code description} / {@code resolutionNotes}）・{@code offRoute} は
 * ここに現れない。認証が無い以上、<strong>追跡番号を手に入れた誰もが見る</strong>。
 * 荷役の作業者名や予定外だった事実は、荷主に伝えるものではなく社内の手がかりである。
 *
 * <p>項目を足すときは [ADR-024] 決定 5 を読み直す。「あると便利」で足したものが、
 * <strong>認証の外へ出る</strong>。
 *
 * @param estimatedArrival 推定到着日。<strong>決まっていなければ null</strong>
 *     ——0 や今日で埋めると、荷主は「今日着く」と読む
 * @param hasException 例外が起きているか。<strong>詳細は返さない</strong>
 * @param urgent 紛失だけが真（決定 3）
 */
public record PublicTrackingResponse(String trackingNumber, String status, String statusLabel,
        String locationName, LocalDate estimatedArrival, boolean hasException, boolean urgent,
        List<PublicTrackingEvent> events, List<PublicTrackingNotice> notices) {

    /** 経過の 1 件。<strong>荷役の種別も作業者も返さない</strong>。 */
    public record PublicTrackingEvent(String occurredAt, String status, String statusLabel,
            String locationName) {

        static PublicTrackingEvent from(TrackingEvent event) {
            return new PublicTrackingEvent(event.occurredAt().toString(),
                    event.trackingStatus().name(), event.trackingStatus().label(),
                    event.location().name());
        }
    }

    /** お知らせの 1 件（[ADR-024] 決定 9）。<strong>メールは送っていない</strong>。 */
    public record PublicTrackingNotice(String noticedAt, String message) {

        static PublicTrackingNotice from(TrackingNotice notice) {
            return new PublicTrackingNotice(notice.noticedAt().toString(), notice.message());
        }
    }

    static PublicTrackingResponse from(TrackingActivity activity, List<TrackingEvent> events,
            List<TrackingNotice> notices) {
        return new PublicTrackingResponse(
                activity.trackingNumber().value(),
                activity.trackingStatus().name(),
                activity.trackingStatus().label(),
                activity.currentLocation().name(),
                activity.estimatedArrival().orElse(null),
                activity.activeException().isPresent(),
                activity.hasUrgentException(),
                events.stream().map(PublicTrackingEvent::from).toList(),
                notices.stream().map(PublicTrackingNotice::from).toList());
    }
}
