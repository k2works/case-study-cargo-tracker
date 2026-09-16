package com.example.cargotracker.handling.infrastructure.persistence;

import com.example.cargotracker.handling.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.shared.domain.location.Location;
import java.util.List;

/**
 * 投影の行から貨物の写しを組み立てる。
 *
 * <p><b>組み立ては 1 か所。</b> 記録の経路（{@code HandlingController}）と
 * 読みの経路（{@code HandlingQueryHandler}）が別々に組み立てると、
 * 片方だけが正しい形になる——予定外の判定は
 * {@link CargoSnapshot#isOffRoute} が 1 か所で答えるのに、
 * その入力の作り方が 2 通りあっては意味がない。</p>
 */
public final class CargoSnapshots {

    private CargoSnapshots() {
    }

    public static CargoSnapshot of(CargoSnapshotMapper.CargoSnapshotRow row,
            List<CargoSnapshotMapper.CargoSnapshotLegRow> legs) {
        return new CargoSnapshot(row.trackingNumber(), row.bookingId(),
                Location.of(row.originUnlocode()), Location.of(row.destinationUnlocode()),
                row.cargoType(),
                legs.stream()
                        .map(leg -> new CargoSnapshot.LegSnapshot(leg.voyageNumber(),
                                Location.of(leg.loadUnlocode()),
                                Location.of(leg.unloadUnlocode())))
                        .toList());
    }
}
