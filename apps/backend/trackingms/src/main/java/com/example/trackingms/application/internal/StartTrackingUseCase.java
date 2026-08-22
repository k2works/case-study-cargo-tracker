package com.example.trackingms.application.internal;

import com.example.shared.domain.model.Location;
import com.example.trackingms.application.port.LocationRepository;
import com.example.trackingms.application.port.TrackingActivityRepository;
import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingBookingId;
import com.example.trackingms.domain.model.TrackingNumber;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 追跡を始める（US14-3）。
 *
 * <p><strong>冪等にする</strong>（[ADR-022] 決定 5）。再試行がある以上、同じイベントが 2 回
 * 届くことは起こる。同じ追跡番号で 2 回来ても 1 件にする。
 *
 * <p><strong>地点はマスタから引く</strong>（[ADR-014]）。イベントが運ぶのは UN/LOCODE だけで、
 * 名称はこちらのマスタが持つ。相手が返した名称をそのまま使うと、地点名の直しが 2 か所に分かれる。
 */
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
     * @throws IllegalArgumentException 地点がマスタに無いとき。<strong>握りつぶさない</strong>
     *     ——黙って作ると、出発地の分からない追跡ができる
     */
    public TrackingActivity start(String trackingNumber, String bookingId,
            String originUnLocode, String destinationUnLocode, LocalDate arrivalDeadline) {
        TrackingNumber number = TrackingNumber.of(trackingNumber);
        Optional<TrackingActivity> existing = activities.findByTrackingNumber(number);
        if (existing.isPresent()) {
            return existing.get();
        }
        return activities.save(TrackingActivity.start(number, TrackingBookingId.of(bookingId),
                requireLocation(originUnLocode, "出発地"),
                requireLocation(destinationUnLocode, "目的地"),
                arrivalDeadline));
    }

    private Location requireLocation(String unLocode, String label) {
        return locations.findByUnLocode(unLocode)
                .orElseThrow(() -> new IllegalArgumentException(
                        label + "が地点マスタにありません: " + unLocode));
    }
}
