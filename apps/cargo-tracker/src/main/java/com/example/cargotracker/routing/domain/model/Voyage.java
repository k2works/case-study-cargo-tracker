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

    /** 積載可能重量。**容量が分からない便を作らない**ため必須である。 */
    private final RoutingWeight capacityWeight;
    private final long version;

    private Voyage(
            VoyageNumber voyageNumber,
            VesselName vesselName,
            CarrierName carrierName,
            Schedule schedule,
            Set<RoutingCargoType> acceptableCargoTypes,
            RoutingWeight capacityWeight,
            long version) {
        this.voyageNumber = voyageNumber;
        this.vesselName = vesselName;
        this.carrierName = carrierName;
        this.schedule = schedule;
        this.acceptableCargoTypes = acceptableCargoTypes;
        this.capacityWeight = capacityWeight;
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
        if (command.capacityWeight() == null) {
            throw new IllegalArgumentException("積載可能重量は必須です");
        }
        return new Voyage(
                command.voyageNumber(),
                command.vesselName(),
                command.carrierName(),
                command.schedule(),
                normalize(command.acceptableCargoTypes()),
                command.capacityWeight(),
                0L);
    }

    /** 永続化された状態から復元する。 */
    public static Voyage reconstruct(
            VoyageNumber voyageNumber,
            VesselName vesselName,
            CarrierName carrierName,
            Schedule schedule,
            Set<RoutingCargoType> acceptableCargoTypes,
            RoutingWeight capacityWeight,
            long version) {
        return new Voyage(
                voyageNumber, vesselName, carrierName, schedule,
                normalize(acceptableCargoTypes), capacityWeight, version);
    }

    /**
     * 運航変更を反映する（US25）。
     *
     * <p><strong>航海番号は変えない。</strong> 変えられると、更新のつもりで
     * 別の便を上書きできてしまう。番号は便そのものの同一性であり、
     * 変更したいなら新しい便を登録する操作になる。
     *
     * <p>スケジュールの連結・時系列は {@link Schedule} が守る。
     * <strong>登録でだけ検査する形にしない</strong> — 更新経路から
     * 不正なスケジュールを作れてしまう。
     *
     * @param command 更新内容（航海番号は現在の便と一致していること）
     * @return 更新後の航海。**元の航海は変えない**（値として扱う）
     */
    public Voyage reschedule(RegisterVoyageCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("更新コマンドは必須です");
        }
        if (!voyageNumber.equals(command.voyageNumber())) {
            throw new IllegalArgumentException("航海番号は変更できません");
        }
        Voyage updated = Voyage.register(command);
        return new Voyage(
                voyageNumber,
                updated.vesselName,
                updated.carrierName,
                updated.schedule,
                updated.acceptableCargoTypes,
                updated.capacityWeight,
                version);
    }

    /** 変更内容（差分）を作る（US25）。 */
    public ScheduleChange changesTo(Voyage updated) {
        return ScheduleChange.between(this, updated);
    }

    private static Set<RoutingCargoType> normalize(Set<RoutingCargoType> types) {
        // **何も運べない航海は業務上あり得ない。**
        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("取り扱える貨物種別を 1 つ以上指定してください");
        }
        return EnumSet.copyOf(types);
    }

    public RoutingWeight capacityWeight() {
        return capacityWeight;
    }

    /**
     * この重量を積めるか（US09）。
     *
     * <p><strong>すでに割り当てた分を差し引いて判断する。</strong> 容量だけを見ると、
     * 何件割り当てても「空きあり」を返し続ける。
     *
     * @param weight         積みたい重量
     * @param assignedWeight すでに割り当て済みの重量
     */
    public boolean hasCapacityFor(RoutingWeight weight, RoutingWeight assignedWeight) {
        java.math.BigDecimal assigned =
                assignedWeight == null ? java.math.BigDecimal.ZERO : assignedWeight.kilograms();
        return capacityWeight.kilograms()
                .subtract(assigned)
                .compareTo(weight.kilograms()) >= 0;
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
