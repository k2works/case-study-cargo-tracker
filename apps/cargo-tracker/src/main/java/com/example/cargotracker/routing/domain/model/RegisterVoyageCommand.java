package com.example.cargotracker.routing.domain.model;

import java.util.Set;

/**
 * 航海スケジュールの新規登録コマンド（US24）。
 *
 * <p>出発港・到着港・出発日・到着日は {@link Schedule} が持つ。**航海の端点を
 * コマンドにも持たせない。** 同じ事実を 2 か所に持つと、区間を足したときに
 * 端点だけ古いままになる。
 *
 * @param voyageNumber        航海番号（必須・一意）
 * @param vesselName          船名（必須）
 * @param carrierName         運送会社（必須）
 * @param schedule            航海スケジュール（必須）
 * @param acceptableCargoTypes 取り扱える貨物種別（1 つ以上）
 */
public record RegisterVoyageCommand(
        VoyageNumber voyageNumber,
        VesselName vesselName,
        CarrierName carrierName,
        Schedule schedule,
        Set<RoutingCargoType> acceptableCargoTypes) {

    public RegisterVoyageCommand {
        // **呼び出し側が後から書き換えられる集合を受け取らない。**
        // 検証を通した後に中身が変わると、不変条件を守った意味が消える。
        // 空の場合は Voyage.register が業務のことばで拒否する
        acceptableCargoTypes =
                acceptableCargoTypes == null ? Set.of() : Set.copyOf(acceptableCargoTypes);
    }

    @Override
    public Set<RoutingCargoType> acceptableCargoTypes() {
        return acceptableCargoTypes;
    }
}
