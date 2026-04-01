package com.example.cargotracker.routing;

import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import com.example.cargotracker.routing.infrastructure.adapters.StubRouteProviderAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StubRouteProviderAdapter")
class StubRouteProviderAdapterTest {

    private final StubRouteProviderAdapter adapter = new StubRouteProviderAdapter();

    @Test
    @DisplayName("2 件以上のルート候補を返す")
    void 二件以上のルート候補を返す() {
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", LocalDate.of(2025, 12, 31),
            CargoType.GENERAL, new BigDecimal("100.0")
        );

        List<RouteCandidate> result = adapter.findRoutes(query);

        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("各候補の transitDays は正の値である")
    void 各候補のtransitDaysは正の値である() {
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", LocalDate.of(2025, 12, 31),
            CargoType.GENERAL, new BigDecimal("100.0")
        );

        List<RouteCandidate> result = adapter.findRoutes(query);

        assertThat(result).allMatch(c -> c.transitDays() > 0);
    }

    @Test
    @DisplayName("各候補の estimatedPrice は正の値である")
    void 各候補のestimatedPriceは正の値である() {
        var query = new RouteSearchQuery(
            "JPTYO", "USNYC", LocalDate.of(2025, 12, 31),
            CargoType.GENERAL, new BigDecimal("100.0")
        );

        List<RouteCandidate> result = adapter.findRoutes(query);

        assertThat(result).allMatch(c -> c.estimatedPrice().compareTo(BigDecimal.ZERO) > 0);
    }
}
