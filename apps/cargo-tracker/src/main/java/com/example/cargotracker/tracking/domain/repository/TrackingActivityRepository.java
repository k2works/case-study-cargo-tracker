package com.example.cargotracker.tracking.domain.repository;

import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import java.util.Optional;

/** 追跡レコードの出力ポート。実装はインフラ層に置く（DIP）。 */
public interface TrackingActivityRepository {

    /** 追跡を始める（US14）。 */
    void save(TrackingActivity activity);

    /**
     * 輸送状態とイベントを保存する（US15）。
     *
     * <p><strong>状態とイベントを 1 つの操作として書く。</strong> 片方だけが残ると、
     * 「積込済なのにイベントが無い」「イベントはあるが未受取のまま」になる。
     *
     * @return 他の更新が先行していれば {@code false}（楽観的ロック）
     */
    boolean update(TrackingActivity activity);

    Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber);

    Optional<TrackingActivity> findByBookingId(TrackingBookingId bookingId);
}
