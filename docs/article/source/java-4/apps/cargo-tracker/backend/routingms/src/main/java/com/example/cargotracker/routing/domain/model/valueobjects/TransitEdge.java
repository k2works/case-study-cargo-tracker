package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;

/**
 * 経路の 1 区間（US08）。「この航海のこの区間に乗る」を表す。
 *
 * <p>{@link CarrierMovement} と似ているが別の型である。{@code CarrierMovement} は
 * 「航海がどう動くか」で {@code Voyage} 集約の一部、こちらは「貨物がどう運ばれるか」で
 * 探索の結果である。<b>どの航海に乗るか</b>（{@code voyageNumber}）を持つ点が違う。
 * 同じ形だからと 1 つにすると、航海の不変条件と探索の都合が同じ型に混ざる。</p>
 */
public record TransitEdge(
        String voyageNumber, Location load, Location unload,
        Instant loadTime, Instant unloadTime) {

    public TransitEdge {
        if (voyageNumber == null || voyageNumber.isBlank()) {
            throw new BusinessRuleViolation("航海番号は必須です");
        }
        if (load == null || unload == null) {
            throw new BusinessRuleViolation("積地と揚地は必須です");
        }
        if (load.equals(unload)) {
            throw new BusinessRuleViolation("積地と揚地が同じです: " + load.unLocode().value());
        }
        if (loadTime == null || unloadTime == null) {
            throw new BusinessRuleViolation("積込日時と荷揚日時は必須です");
        }
        if (!unloadTime.isAfter(loadTime)) {
            throw new BusinessRuleViolation(
                    "荷揚日時は積込日時より後である必要があります: " + loadTime + " → " + unloadTime);
        }
    }
}
