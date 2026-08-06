package com.example.cargotracker.routing.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 航海。Routing Context の集約ルート。
 *
 * <p>航海の端点（出発地・目的地）は {@link Schedule} から導く。**保持しない。**
 * 同じ事実を 2 か所に持つと、区間を足したときに端点だけ古いままになる。
 *
 * <p><strong>Setter を持たない。</strong> スケジュールの変更は US25 で、
 * 業務のことばで名づけた振る舞いとして追加する。
 */
public class Voyage {

    private final VoyageNumber voyageNumber;
    private final VesselName vesselName;
    private final CarrierName carrierName;
    private final Schedule schedule;
    private final Set<RoutingCargoType> acceptableCargoTypes;
    private final long version;

    private Voyage(
            VoyageNumber voyageNumber,
            VesselName vesselName,
            CarrierName carrierName,
            Schedule schedule,
            Set<RoutingCargoType> acceptableCargoTypes,
            long version) {
        this.voyageNumber = voyageNumber;
        this.vesselName = vesselName;
        this.carrierName = carrierName;
        this.schedule = schedule;
        this.acceptableCargoTypes = acceptableCargoTypes;
        this.version = version;
    }

    /** 航海スケジュールを新規登録する（US24）。 */
    public static Voyage register(RegisterVoyageCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("登録コマンドは必須です");
        }
        if (command.voyageNumber() == null) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        if (command.vesselName() == null) {
            throw new IllegalArgumentException("船名は必須です");
        }
        if (command.carrierName() == null) {
            throw new IllegalArgumentException("運送会社は必須です");
        }
        if (command.schedule() == null) {
            throw new IllegalArgumentException("航海スケジュールは必須です");
        }
        return new Voyage(
                command.voyageNumber(),
                command.vesselName(),
                command.carrierName(),
                command.schedule(),
                正規化する(command.acceptableCargoTypes()),
                0L);
    }

    /** 永続化された状態から復元する。 */
    public static Voyage reconstruct(
            VoyageNumber voyageNumber,
            VesselName vesselName,
            CarrierName carrierName,
            Schedule schedule,
            Set<RoutingCargoType> acceptableCargoTypes,
            long version) {
        return new Voyage(
                voyageNumber, vesselName, carrierName, schedule,
                正規化する(acceptableCargoTypes), version);
    }

    private static Set<RoutingCargoType> 正規化する(Set<RoutingCargoType> types) {
        // **何も運べない航海は業務上あり得ない。**
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("取り扱える貨物種別を 1 つ以上指定してください");
        }
        return EnumSet.copyOf(types);
    }

    /** この航海がその貨物種別を運べるか。 */
    public boolean accepts(RoutingCargoType cargoType) {
        return acceptableCargoTypes.contains(cargoType);
    }

    /** 出発地（最初の区間の出発地）。 */
    public Location origin() {
        return schedule.origin();
    }

    /** 目的地（最後の区間の到着地）。 */
    public Location destination() {
        return schedule.destination();
    }

    /** 寄港地。直行便では空。 */
    public List<Location> callingPorts() {
        return schedule.callingPorts();
    }

    /**
     * 指定した港をこの航海が出発する時刻。
     *
     * @return 立ち寄らない港なら空
     */
    public Optional<Instant> departureTime(Location location) {
        return schedule.carrierMovements().stream()
                .filter(m -> m.departureLocation().equals(location))
                .map(CarrierMovement::departureTime)
                .findFirst();
    }

    /**
     * 指定した港にこの航海が到着する時刻。
     *
     * @return 立ち寄らない港なら空
     */
    public Optional<Instant> arrivalTime(Location location) {
        return schedule.carrierMovements().stream()
                .filter(m -> m.arrivalLocation().equals(location))
                .map(CarrierMovement::arrivalTime)
                .findFirst();
    }

    public VoyageNumber voyageNumber() {
        return voyageNumber;
    }

    public VesselName vesselName() {
        return vesselName;
    }

    public CarrierName carrierName() {
        return carrierName;
    }

    public Schedule schedule() {
        return schedule;
    }

    public Set<RoutingCargoType> acceptableCargoTypes() {
        return Set.copyOf(acceptableCargoTypes);
    }

    public long version() {
        return version;
    }
}
