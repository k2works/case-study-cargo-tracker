package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;

/**
 * 航海内の港間移動（Voyage 不変条件 3）。
 *
 * <p>時刻は {@code Instant} で持つ。港のローカル時刻で入力・表示し、保存は
 * {@code TIMESTAMPTZ}（non_functional.md）。{@code LocalDateTime} は時間帯を持たず、
 * 出発港と到着港の時間帯が違う航海では「どちらの時刻か」が決まらない。</p>
 */
public record CarrierMovement(
        Location departure, Location arrival, Instant departureTime, Instant arrivalTime) {

    public CarrierMovement {
        if (departure == null || arrival == null) {
            throw new BusinessRuleViolation("出発地と到着地は必須です");
        }
        if (departure.equals(arrival)) {
            throw new BusinessRuleViolation(
                    "出発地と到着地が同じです: " + departure.unLocode().value());
        }
        if (departureTime == null || arrivalTime == null) {
            throw new BusinessRuleViolation("出発日時と到着日時は必須です");
        }
        if (!arrivalTime.isAfter(departureTime)) {
            // 不変条件 3。等しい場合も断る。同時刻の出発と到着は港の移動になっていない。
            throw new BusinessRuleViolation(
                    "到着日時は出発日時より後である必要があります: " + departureTime + " → " + arrivalTime);
        }
    }
}
