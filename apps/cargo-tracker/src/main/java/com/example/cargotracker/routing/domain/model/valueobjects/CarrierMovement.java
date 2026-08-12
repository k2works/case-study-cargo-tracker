package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Instant;

/**
 * 運送区間。出発地・到着地・出発時刻・到着時刻を持つ移動の単位。
 *
 * <p>ビジネスルール 3（{@code domain-model.md}）: 出発地と到着地は異なる。
 *
 * <p>時刻は {@link Instant}（時点）で持つ。**現地時刻で持つと、港ごとの
 * タイムゾーンを取り違えたときに気づけない。** 現地時刻が要る場面
 * （到着期限の判定）では、港のタイムゾーンで変換する。
 *
 * @param departureLocation 出発地
 * @param arrivalLocation   到着地
 * @param departureTime     出発時刻
 * @param arrivalTime       到着時刻（出発より後）
 */
public record CarrierMovement(
        Location departureLocation,
        Location arrivalLocation,
        Instant departureTime,
        Instant arrivalTime) {

    public CarrierMovement {
        if (departureLocation == null || arrivalLocation == null) {
            throw new IllegalArgumentException("運送区間の出発地と到着地は必須です");
        }
        if (departureLocation.equals(arrivalLocation)) {
            throw new IllegalArgumentException(
                    "運送区間の出発地と到着地は異なる地点です: " + departureLocation.unlocode());
        }
        if (departureTime == null || arrivalTime == null) {
            throw new IllegalArgumentException("運送区間の出発時刻と到着時刻は必須です");
        }
        // **同時刻も認めない。** 出発と到着が同じ時点なら移動していない
        if (!arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException(
                    "運送区間の到着時刻は出発時刻より後です: " + departureTime + " → " + arrivalTime);
        }
    }

    public static CarrierMovement of(
            Location from, Location to, Instant departureTime, Instant arrivalTime) {
        return new CarrierMovement(from, to, departureTime, arrivalTime);
    }
}
