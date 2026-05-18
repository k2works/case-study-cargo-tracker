package com.example.cargotracker.handlingms.infrastructure.persistence;

import com.example.cargotracker.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.handlingms.domain.ports.CargoSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link CargoSnapshotRepository} の MyBatis 実装。
 *
 * <p>{@code cargo_snapshot} テーブルを参照して {@link CargoSnapshot} を引当する。
 * Booking Context への直接依存を持たないように ACL として機能する。</p>
 */
@Repository
public class MyBatisCargoSnapshotRepository implements CargoSnapshotRepository {

    private final CargoSnapshotMapper mapper;

    public MyBatisCargoSnapshotRepository(CargoSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CargoSnapshot> findByTrackingNumber(TrackingNumber trackingNumber) {
        CargoSnapshotRecord record = mapper.findByTrackingNumber(trackingNumber.value());
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(new CargoSnapshot(
                record.getBookingId(),
                trackingNumber,
                Location.of(record.getOriginUnlocode()),
                Location.of(record.getDestinationUnlocode()),
                record.getCargoType()));
    }
}
