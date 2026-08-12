package com.example.cargotracker.booking.infrastructure.acl;

import com.example.cargotracker.booking.domain.model.aggregates.Cargo;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.handling.application.internal.outboundservices.acl.CargoSnapshots;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link CargoSnapshots} の実装（ACL のアダプタ）。
 *
 * <p><strong>渡すのは素の値だけである。</strong> {@code Cargo} や {@code Leg} を
 * そのまま渡すと、荷役モジュールが Booking のドメインを参照することになる
 * （ArchUnit ルール 4）。
 *
 * <p>写しは<strong>その場で作って渡すだけ</strong>であり、保存しない。保存すると、
 * 予約が変わったときに古い写しで誤配を判定することになる。
 */
@Component
public class CargoSnapshotsAdapter implements CargoSnapshots {

    private final CargoRepository cargoRepository;

    public CargoSnapshotsAdapter(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
    }

    @Override
    public Optional<CargoSnapshots.Snapshot> findByTrackingNumber(String trackingNumber) {
        return cargoRepository.findByTrackingNumber(trackingNumber)
                .map(CargoSnapshotsAdapter::toSnapshot);
    }

    private static CargoSnapshots.Snapshot toSnapshot(Cargo cargo) {
        List<CargoSnapshots.Leg> legs = cargo.cargoItinerary() == null
                ? List.of()
                : cargo.cargoItinerary().legs().stream()
                        .map(leg -> new CargoSnapshots.Leg(
                                leg.voyageNumber(),
                                leg.loadLocation().unlocode(),
                                leg.unloadLocation().unlocode()))
                        .toList();
        return new CargoSnapshots.Snapshot(
                cargo.bookingId().value().toString(),
                cargo.routeSpecification().origin().unlocode(),
                cargo.routeSpecification().destination().unlocode(),
                // 引取時の本人確認に使う（US16）。**荷受人は未登録でありうる**
                cargo.consignee() == null ? null : cargo.consignee().name(),
                // **照合する相手**（US35）。確定前・旧い行では無い
                cargo.claimCode() == null ? null : cargo.claimCode().value(),
                // **精算済みは訂正・取り消しできない**（US36）
                cargo.bookingStatus().name(),
                legs);
    }
}
