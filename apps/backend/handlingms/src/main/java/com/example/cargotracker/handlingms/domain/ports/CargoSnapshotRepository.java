package com.example.cargotracker.handlingms.domain.ports;

import com.example.cargotracker.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;

import java.util.Optional;

/**
 * CargoSnapshot リポジトリポート（ACL）。
 *
 * <p>handlingms は bookingms の Cargo に直接依存せず、本ポートを介して
 * 追跡番号から CargoSnapshot を引当する。実装は infrastructure 層で
 * MyBatis Mapper 経由で行う（{@code cargo_snapshot} テーブル）。</p>
 *
 * <p>関連 ADR: ADR-0012 handlingms と trackingms の責務分離</p>
 */
public interface CargoSnapshotRepository {

    /**
     * 追跡番号から CargoSnapshot を引当する。
     *
     * @param trackingNumber 追跡番号
     * @return CargoSnapshot（存在しない場合は空）
     */
    Optional<CargoSnapshot> findByTrackingNumber(TrackingNumber trackingNumber);
}
