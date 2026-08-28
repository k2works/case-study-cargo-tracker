package com.example.bookingms.domain.model.valueobjects;

import com.example.shared.domain.model.Location;
import java.time.Instant;

/**
 * 輸送区間。旅程のうち、1 つの航海で運ばれる 1 区間（US09）。
 *
 * <p>Routing Context の {@code TransitEdge} とは<strong>別の型</strong>である。あちらは
 * 都度算出して捨てる探索結果、こちらは予約に紐付いて残る記録である。変換は ACL で行い、
 * routingms の型をここへ持ち込まない（BC 独立性）。
 *
 * @param voyageNumber どの航海で運ぶか
 * @param loadLocation 積込地
 * @param unloadLocation 荷降し地
 * @param loadTime 積込日時
 * @param unloadTime 荷降し日時
 */
public record Leg(
        VoyageNumber voyageNumber,
        Location loadLocation,
        Location unloadLocation,
        Instant loadTime,
        Instant unloadTime) {

    /** 新規に組み立てる。ここでだけ検査する。 */
    public static Leg of(VoyageNumber voyageNumber, Location loadLocation, Location unloadLocation,
            Instant loadTime, Instant unloadTime) {
        if (voyageNumber == null) {
            throw new IllegalArgumentException("どの航海で運ぶかは必須です");
        }
        if (loadLocation == null || unloadLocation == null) {
            throw new IllegalArgumentException("区間の積込地と荷降し地は必須です");
        }
        if (loadLocation.equals(unloadLocation)) {
            throw new IllegalArgumentException("区間の積込地と荷降し地は同じにできません");
        }
        if (loadTime == null || unloadTime == null) {
            throw new IllegalArgumentException("区間の積込日時と荷降し日時は必須です");
        }
        if (!unloadTime.isAfter(loadTime)) {
            throw new IllegalArgumentException("荷降し日時は積込日時より後にしてください");
        }
        return new Leg(voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static Leg restore(VoyageNumber voyageNumber, Location loadLocation,
            Location unloadLocation, Instant loadTime, Instant unloadTime) {
        return new Leg(voyageNumber, loadLocation, unloadLocation, loadTime, unloadTime);
    }
}
