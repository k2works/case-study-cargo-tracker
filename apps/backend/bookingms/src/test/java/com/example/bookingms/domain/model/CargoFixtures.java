package com.example.bookingms.domain.model;

import com.example.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

/**
 * 貨物予約のテストが共有する組み立て。
 *
 * <p>集約のテストを局面ごとに分けた（受付・経路の割り当て・確定）とき、この組み立てを
 * 写すと、集約の作り方が変わったときに<strong>片方だけ直る</strong>。
 */
final class CargoFixtures {

    private CargoFixtures() {
    }

    static final RouteSpecification ROUTE = RouteSpecification.restore(
            Location.of("JPTYO", "Tokyo"), Location.of("USLAX", "Los Angeles"),
            LocalDate.of(2026, Month.SEPTEMBER, 1), LocalDate.of(2026, Month.SEPTEMBER, 20));

    static final HazardousDeclaration DECLARATION =
            HazardousDeclaration.of("3", "UN1263", "PAINT");

    static final TemperatureRequirement TEMPERATURE =
            TemperatureRequirement.of(new BigDecimal("-20"), new BigDecimal("-15"));

    static CargoSpecification specification(CargoType type,
            HazardousDeclaration declaration, TemperatureRequirement temperature) {
        return new CargoSpecification(type, new BigDecimal("12000"), 20, "電子部品",
                Dimensions.of(new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100")),
                declaration, temperature);
    }
}
