package com.example.trackingms.application.internal.commandservices;

import com.example.shared.domain.model.Location;
import com.example.trackingms.domain.repository.LocationRepository;
import com.example.trackingms.domain.repository.TrackingActivityRepository;
import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * 追跡を始める（US14-3）。
 *
 * <p><strong>冪等にする</strong>（[ADR-022] 決定 5）。再試行がある以上、同じイベントが 2 回
 * 届くことは起こる。同じ追跡番号で 2 回来ても 1 件にする。
 *
 * <p><strong>地点はマスタから引く</strong>（[ADR-014]）。イベントが運ぶのは UN/LOCODE だけで、
 * 名称はこちらのマスタが持つ。相手が返した名称をそのまま使うと、地点名の直しが 2 か所に分かれる。
 */
@Service
public class StartTrackingUseCase {

    private final TrackingActivityRepository activities;
    private final LocationRepository locations;

    public StartTrackingUseCase(TrackingActivityRepository activities,
            LocationRepository locations) {
        this.activities = activities;
        this.locations = locations;
    }

    /**
     * 追跡を始める。すでにあればそれを返す（作り直さない）。
     *
     * <p><strong>探してから書く形にはしない。</strong>重複かどうかは保存先の一意制約が決める。
     *
     * @throws IllegalArgumentException 地点がマスタに無いとき。<strong>握りつぶさない</strong>
     *     ——黙って作ると、出発地の分からない追跡ができる
     */
    public TrackingActivity start(String trackingNumber, String bookingId,
            String originUnLocode, String destinationUnLocode, LocalDate arrivalDeadline,
            LocalDate estimatedArrival) {
        TrackingNumber number = TrackingNumber.of(trackingNumber);
        TrackingActivity existingOrNew = activities.saveIfAbsent(
                TrackingActivity.start(number, TrackingBookingId.of(bookingId),
                        requireLocation(originUnLocode, "出発地"),
                        requireLocation(destinationUnLocode, "目的地"),
                        arrivalDeadline)
                        // **推定到着日は追跡の作成と同じイベントで届く**（[ADR-024] 決定 4）。
                        // 別のイベントで送ると、2 つのイベントが別々のキューを通るため順序が
                        // 保証されず、先に届いた到着日は引く相手が無く捨てられる
                        .withEstimatedArrival(estimatedArrival));

        return applyRevisedEstimate(existingOrNew, estimatedArrival);
    }

    /**
     * 届いた推定到着日が新しければ反映する。
     *
     * <p><strong>作成は冪等でよいが、推定到着日まで冪等にしない</strong>（IT9 返済枠 0.5）。
     * 経路を組み直した貨物は新しい見込みを持って届く。作成済みだからとイベントを丸ごと
     * 捨てると、荷主は古い到着日を見続ける。
     *
     * <p><strong>追跡は作り直さない。</strong>作り直すと、これまでの経過が消える。
     *
     * <p><strong>空では上書きしない。</strong>推定到着日を運ばないイベントで消すと、
     * いったん出せていた見込みが「未定」に戻る。
     *
     * <p><strong>変わっていなければ書かない。</strong>再試行で同じ中身が 2 回届くのは
     * 普通のことであり、毎回書くと何も変わっていない更新が記録に積まれる。
     */
    private TrackingActivity applyRevisedEstimate(TrackingActivity activity,
            LocalDate estimatedArrival) {
        if (estimatedArrival == null
                || activity.estimatedArrival().filter(estimatedArrival::equals).isPresent()) {
            return activity;
        }
        TrackingActivity revised = activity.withEstimatedArrival(estimatedArrival);
        activities.updateStatus(revised);
        return revised;
    }

    private Location requireLocation(String unLocode, String label) {
        return locations.findByUnLocode(unLocode)
                .orElseThrow(() -> new IllegalArgumentException(
                        label + "が地点マスタにありません: " + unLocode));
    }
}
