package com.example.cargotracker.tracking.handling.domain.repository;

import com.example.cargotracker.tracking.handling.domain.model.CargoBookingId;
import com.example.cargotracker.tracking.handling.domain.model.HandlingActivity;
import java.util.List;

/** 荷役作業の出力ポート。実装はインフラ層に置く（DIP）。 */
public interface HandlingActivityRepository {

    /** 荷役作業を登録する（US15）。 */
    void save(HandlingActivity activity);

    /** 予約ごとの荷役履歴を、作業日時の新しい順で返す。 */
    List<HandlingActivity> findByBookingId(CargoBookingId bookingId);

    /**
     * 荷役履歴を新しい順で返す（荷役作業一覧）。
     *
     * <p><strong>登録した作業を先頭に表示する</strong>ため、既定の並びは
     * 作業日時の新しい順である（{@code ui_design.md}）。
     *
     * @param limit 取得件数の上限
     */
    List<HandlingActivity> findRecent(int limit);
}
