package com.example.cargotracker.tracking.domain.repository;

import com.example.cargotracker.tracking.domain.model.TrackingActivity;
import com.example.cargotracker.tracking.domain.model.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.TrackingNumber;
import java.util.Optional;
import javax.annotation.CheckReturnValue;

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
    @CheckReturnValue
    boolean update(TrackingActivity activity);

    Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber);

    Optional<TrackingActivity> findByBookingId(TrackingBookingId bookingId);

    /**
     * 例外を持つ追跡番号（IT13 レビュー C4）。
     *
     * <p><strong>1 件ずつ聞かない。</strong> 一覧の行数だけ問い合わせが飛ぶ。
     */
    java.util.Set<String> findTrackingNumbersWithUnresolvedException(
            java.util.Collection<String> trackingNumbers);
}
