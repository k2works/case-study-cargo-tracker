package com.example.routingms.domain.model;

import com.example.shared.domain.model.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 航海スケジュール。時系列につながった運送区間の並び。
 *
 * <p>「つながっている」ことをここで守る。前の区間の到着地から次の区間が出ていない並びは、
 * 港の集合としては同じでも航海ではない。通すと、経路候補算出（IT4）が実在しない乗り継ぎを提案する。
 */
public final class Schedule {

    private final List<CarrierMovement> carrierMovements;

    private Schedule(List<CarrierMovement> carrierMovements) {
        this.carrierMovements = List.copyOf(carrierMovements);
    }

    /** 新規に受け入れる。ここでだけ検査する。 */
    public static Schedule of(List<CarrierMovement> carrierMovements) {
        if (carrierMovements == null || carrierMovements.isEmpty()) {
            throw new IllegalArgumentException("航海には少なくとも 1 つの区間が必要です");
        }
        for (int i = 1; i < carrierMovements.size(); i++) {
            CarrierMovement previous = carrierMovements.get(i - 1);
            CarrierMovement current = carrierMovements.get(i);
            if (!previous.arrivalLocation().equals(current.departureLocation())) {
                throw new IllegalArgumentException(
                        "区間がつながっていません。前の区間の到着地から次の区間が出発するようにしてください");
            }
            if (current.departureTime().isBefore(previous.arrivalTime())) {
                // 到着と同時刻の出発（滞船 0 分）は認める。通過するだけの港で実際に起きる
                throw new IllegalArgumentException("次の区間が前の区間の到着より前に出発しています");
            }
        }
        return new Schedule(carrierMovements);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static Schedule restore(List<CarrierMovement> carrierMovements) {
        return new Schedule(carrierMovements);
    }

    public List<CarrierMovement> carrierMovements() {
        return carrierMovements;
    }

    /** 寄港地を順序どおりに返す。最初の出発地から、最後の到着地まで。 */
    public List<Location> callingPorts() {
        List<Location> ports = new ArrayList<>();
        ports.add(carrierMovements.get(0).departureLocation());
        for (CarrierMovement movement : carrierMovements) {
            ports.add(movement.arrivalLocation());
        }
        return List.copyOf(ports);
    }

    public Location origin() {
        return carrierMovements.get(0).departureLocation();
    }

    public Location destination() {
        return carrierMovements.get(carrierMovements.size() - 1).arrivalLocation();
    }

    /** その港を出発する時刻。最終到着地には出発が無いため空を返す。 */
    public Optional<java.time.Instant> departureTime(Location location) {
        return carrierMovements.stream()
                .filter(movement -> movement.departureLocation().equals(location))
                .map(CarrierMovement::departureTime)
                .findFirst();
    }

    /** その港に到着する時刻。出発地には到着が無いため空を返す。 */
    public Optional<java.time.Instant> arrivalTime(Location location) {
        return carrierMovements.stream()
                .filter(movement -> movement.arrivalLocation().equals(location))
                .map(CarrierMovement::arrivalTime)
                .findFirst();
    }

    /** 寄港の並びにおける位置。寄港しない港は空を返す。 */
    Optional<Integer> callingOrderOf(Location location) {
        List<Location> ports = callingPorts();
        for (int i = 0; i < ports.size(); i++) {
            if (ports.get(i).equals(location)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Schedule schedule && carrierMovements.equals(schedule.carrierMovements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carrierMovements);
    }
}
