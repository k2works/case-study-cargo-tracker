package com.example.handlingms.infrastructure.repositories.mybatis;

import com.example.handlingms.domain.projections.CargoSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * cargo_snapshot Read Model 用 Mapper（CargoSnapshot ACL / IT5 3.1）。
 *
 * <p>SQL は {@code resources/mapper/CargoSnapshotMapper.xml} で定義する。</p>
 */
@Mapper
public interface CargoSnapshotMapper {

    /** TrackingIssuanceRequestedEvent 受信時に新規作成または冪等更新する（origin/destination/cargoType）。 */
    void upsert(@Param("bookingId") String bookingId,
                @Param("originUnlocode") String originUnlocode,
                @Param("destinationUnlocode") String destinationUnlocode,
                @Param("cargoType") String cargoType);

    /** CargoTrackedEvent 受信時に tracking_number を確定する。 */
    int updateTrackingNumber(@Param("bookingId") String bookingId,
                             @Param("trackingNumber") String trackingNumber);

    CargoSnapshot findByBookingId(@Param("bookingId") String bookingId);

    CargoSnapshot findByTrackingNumber(@Param("trackingNumber") String trackingNumber);
}
