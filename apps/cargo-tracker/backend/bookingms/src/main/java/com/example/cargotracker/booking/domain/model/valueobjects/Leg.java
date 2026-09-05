package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;

/**
 * 旅程の 1 区間（US09。domain-model.md）。「この航海のこの区間で運ぶ」。
 *
 * <p>航海番号は<b>文字列で持つ</b>。{@code VoyageNumber} は routingms の識別子型で、
 * 共有カーネルには置かない（domain-model.md）。ここで型を持ち込むと、航海番号の
 * 規則が変わったときに予約側が巻き込まれる。</p>
 */
public record Leg(
        String voyageNumber, Location load, Location unload,
        Instant loadTime, Instant unloadTime) {

    public Leg {
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
