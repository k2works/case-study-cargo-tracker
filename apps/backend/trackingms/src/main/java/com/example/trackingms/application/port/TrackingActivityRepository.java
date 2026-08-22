package com.example.trackingms.application.port;

import com.example.trackingms.domain.model.TrackingActivity;
import com.example.trackingms.domain.model.TrackingNumber;
import java.util.Optional;

/** 追跡の保存先（出力ポート）。 */
public interface TrackingActivityRepository {

    TrackingActivity save(TrackingActivity activity);

    /** 追跡番号から探す。照会の入口であり、二重に作らないための確認にも使う。 */
    Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber);
}
