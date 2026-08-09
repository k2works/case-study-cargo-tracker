package com.example.cargotracker.handling.domain.repository;

import com.example.cargotracker.handling.domain.model.CargoBookingId;
import com.example.cargotracker.handling.domain.model.HandlingActivity;
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

    /**
     * 訂正・取り消しの対象になる荷役を引く（US36）。
     *
     * <p><strong>集約を返さない。</strong> 申請の可否を決めるのに要るのは、
     * 予約 ID・追跡番号・取り消し済みかどうかだけである。
     */
    java.util.Optional<CancellableHandling> findCancellable(long handlingActivityId);

    /**
     * 取り消された事実を書く（US36）。
     *
     * <p><strong>行は消さない。</strong> 誰がいつ何を登録し、誰がいつ取り消したかが
     * 読めなくなると、事故時に経緯を追えない。
     *
     * @return 書けたか。**すでに取り消されていれば false**
     */
    boolean markCancelled(long handlingActivityId, java.time.Instant at, String by);

    /**
     * 訂正・取り消しの対象（表示と判断に要る値だけ）。
     *
     * @param cancelled すでに取り消されているか
     */
    record CancellableHandling(
            long id, java.util.UUID bookingId, String trackingNumber, boolean cancelled) {
    }
}
