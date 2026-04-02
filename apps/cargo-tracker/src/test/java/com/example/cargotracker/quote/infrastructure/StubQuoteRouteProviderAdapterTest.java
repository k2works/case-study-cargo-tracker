package com.example.cargotracker.quote.infrastructure;

import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import com.example.cargotracker.quote.infrastructure.adapters.StubQuoteRouteProviderAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StubQuoteRouteProviderAdapter")
class StubQuoteRouteProviderAdapterTest {

    private StubQuoteRouteProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new StubQuoteRouteProviderAdapter();
    }

    @Test
    @DisplayName("findRouteOptions は固定のルート候補を返す")
    void findRouteOptionsは固定のルート候補を返す() {
        List<RouteOption> result = adapter.findRouteOptions(anyCondition());

        assertThat(result).isNotEmpty();
    }

    @Test
    @DisplayName("findRouteOptions は 2 件以上のルート候補を返す")
    void findRouteOptionsは2件以上のルート候補を返す() {
        List<RouteOption> result = adapter.findRouteOptions(anyCondition());

        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("各ルート候補の所要日数は正の値である")
    void 各ルート候補の所要日数は正の値である() {
        List<RouteOption> result = adapter.findRouteOptions(anyCondition());

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(opt ->
                assertThat(opt.transitDays()).isGreaterThan(0)
        );
    }

    @Test
    @DisplayName("各ルート候補の概算料金は正の値である")
    void 各ルート候補の概算料金は正の値である() {
        List<RouteOption> result = adapter.findRouteOptions(anyCondition());

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(opt ->
                assertThat(opt.estimatedPrice()).isGreaterThan(BigDecimal.ZERO)
        );
    }

    private QuoteCondition anyCondition() {
        return new QuoteCondition(
                "JPTYO",
                "USNYC",
                LocalDate.of(2025, 12, 31),
                CargoType.GENERAL_CARGO,
                new BigDecimal("1000")
        );
    }
}
