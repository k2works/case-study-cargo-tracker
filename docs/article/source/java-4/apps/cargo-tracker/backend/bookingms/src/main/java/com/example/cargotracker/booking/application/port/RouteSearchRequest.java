package com.example.cargotracker.booking.application.port;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.LocalDate;
import java.util.List;

/**
 * 経路候補を探す条件（US08）。
 *
 * <p><b>集約の {@code RouteSpecification} とは別の型にする。</b> あちらは「予約が
 * 満たすべきこと」（端点と期限）で、集約が持つ。こちらは「どう探すか」で、貨物種別・
 * 除外港・探索の起点を含む。集約に探索の都合を足すと、経路仕様が変わる理由が
 * 増える（設計上は US10 の条件調整で集約側に入る）。</p>
 *
 * <p><b>条件は投影（{@code cargo_summary}）から組む。</b> 候補算出は Query 側なので
 * 集約を読み出さない。画面から組み立てると、予約の期限を直したのに古い期限で
 * 探すことが起きる。</p>
 *
 * @param departFrom 探索の起点。誤配の再設計（IT11）で現在地を渡す。通常は null
 */
public record RouteSearchRequest(
        Location origin,
        Location destination,
        LocalDate arrivalDeadline,
        CargoType cargoType,
        List<Location> excludePorts,
        Location departFrom) {

    public RouteSearchRequest {
        if (origin == null || destination == null) {
            throw new BusinessRuleViolation("出発地と目的地は必須です");
        }
        if (origin.equals(destination)) {
            throw new BusinessRuleViolation(
                    "出発地と目的地が同じです: " + origin.unLocode().value());
        }
        if (arrivalDeadline == null) {
            throw new BusinessRuleViolation("到着期限は必須です");
        }
        if (cargoType == null) {
            throw new BusinessRuleViolation("貨物種別は必須です");
        }
        excludePorts = excludePorts == null ? List.of() : List.copyOf(excludePorts);
    }

    /** 除外港も起点も無い、ふつうの探索。 */
    public static RouteSearchRequest of(Location origin, Location destination,
            LocalDate arrivalDeadline, CargoType cargoType) {
        return new RouteSearchRequest(origin, destination, arrivalDeadline, cargoType,
                List.of(), null);
    }
}
