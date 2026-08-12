package com.example.cargotracker.routing.domain.model.valueobjects;
import com.example.cargotracker.routing.domain.model.entities.CarrierMovement;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.util.List;

/**
 * 航海スケジュール。時系列に連なる運送区間の並び。
 *
 * <p>ビジネスルール 2（{@code domain-model.md}）: 時系列順の {@link CarrierMovement} で
 * 構成される。本クラスが守るのは<strong>区間をまたぐ 2 つの制約</strong>である。
 *
 * <ol>
 *   <li><strong>連結</strong>: 区間 n の到着港 = 区間 n+1 の出発港。違えば貨物は途中で消える</li>
 *   <li><strong>時系列</strong>: 区間 n+1 の出発 ≧ 区間 n の到着。着く前に次の船は出ない</li>
 * </ol>
 *
 * <p><strong>どちらも DB の CHECK 制約では守れない。</strong> 1 行の中で完結せず
 * 行をまたぐためである。**ここで守らなければ、どこでも守られない。**
 *
 * @param carrierMovements 運送区間（1 つ以上）
 */
public record Schedule(List<CarrierMovement> carrierMovements) {

    public Schedule {
        if (carrierMovements == null || carrierMovements.isEmpty()) {
            throw new IllegalArgumentException("航海スケジュールは 1 つ以上の運送区間を持ちます");
        }
        carrierMovements = List.copyOf(carrierMovements);
        validate(carrierMovements);
    }

    private static void validate(List<CarrierMovement> movements) {
        for (int i = 1; i < movements.size(); i++) {
            CarrierMovement previous = movements.get(i - 1);
            CarrierMovement current = movements.get(i);

            if (!previous.arrivalLocation().equals(current.departureLocation())) {
                throw new IllegalArgumentException(
                        "運送区間がつながっていません: %s に到着した後 %s から出発しています"
                                .formatted(
                                        previous.arrivalLocation().unlocode(),
                                        current.departureLocation().unlocode()));
            }
            // 同時刻の乗り継ぎは認める（接続時間 0 は運用上ありうる）
            if (current.departureTime().isBefore(previous.arrivalTime())) {
                throw new IllegalArgumentException(
                        "前の区間の到着より前に出発しています: %s 到着 %s、次の出発 %s"
                                .formatted(
                                        previous.arrivalLocation().unlocode(),
                                        previous.arrivalTime(),
                                        current.departureTime()));
            }
        }
    }

    public static Schedule of(List<CarrierMovement> carrierMovements) {
        return new Schedule(carrierMovements);
    }

    /** 航海の出発地（最初の区間の出発地）。 */
    public Location origin() {
        return carrierMovements.getFirst().departureLocation();
    }

    /** 航海の目的地（最後の区間の到着地）。 */
    public Location destination() {
        return carrierMovements.getLast().arrivalLocation();
    }

    /**
     * 寄港地（出発地と目的地を除く途中の港）。
     *
     * <p>直行便では空になる。
     */
    public List<Location> callingPorts() {
        return carrierMovements.stream()
                .limit(carrierMovements.size() - 1L)
                .map(CarrierMovement::arrivalLocation)
                .toList();
    }
}
