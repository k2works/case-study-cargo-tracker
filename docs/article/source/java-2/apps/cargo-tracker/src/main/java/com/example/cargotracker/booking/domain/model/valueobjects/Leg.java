package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Instant;

/**
 * 輸送区間。単一航海での積込港から荷降港までの区間。
 *
 * <p>航海番号は<strong>文字列で持つ</strong>。Routing の {@code VoyageNumber} を
 * 参照すると BC 間の直接参照になる（ADR-005・ArchUnit ルール 4）。
 * 貨物の側が知る必要があるのは「どの便で運ぶか」という事実だけである。
 *
 * @param voyageNumber   航海番号
 * @param loadLocation   積込港
 * @param unloadLocation 荷降港
 * @param loadTime       積込予定日時
 * @param unloadTime     荷降予定日時
 */
public record Leg(
        String voyageNumber,
        Location loadLocation,
        Location unloadLocation,
        Instant loadTime,
        Instant unloadTime) {

    public Leg {
        if (voyageNumber == null || voyageNumber.isBlank()) {
            // どの便で運ぶかが分からない区間は、荷役の照合にも使えない
            throw new IllegalArgumentException("航海番号は必須です");
        }
        if (loadLocation == null || unloadLocation == null) {
            throw new IllegalArgumentException("積込港と荷降港は必須です");
        }
        if (loadLocation.equals(unloadLocation)) {
            throw new IllegalArgumentException(
                    "積込港と荷降港が同じです: " + loadLocation.unlocode());
        }
        if (loadTime == null || unloadTime == null) {
            throw new IllegalArgumentException("積込日時と荷降日時は必須です");
        }
        if (!unloadTime.isAfter(loadTime)) {
            throw new IllegalArgumentException("荷降日時は積込日時より後です");
        }
        voyageNumber = voyageNumber.strip();
    }

    public static Leg of(
            String voyageNumber,
            Location loadLocation,
            Location unloadLocation,
            Instant loadTime,
            Instant unloadTime) {
        return new Leg(voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime);
    }
}
