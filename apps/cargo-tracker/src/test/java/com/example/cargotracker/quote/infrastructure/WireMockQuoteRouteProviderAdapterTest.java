package com.example.cargotracker.quote.infrastructure;

import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import com.example.cargotracker.quote.infrastructure.adapters.WireMockQuoteRouteProviderAdapter;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WireMockQuoteRouteProviderAdapter")
class WireMockQuoteRouteProviderAdapterTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private WireMockQuoteRouteProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new WireMockQuoteRouteProviderAdapter(
                "http://localhost:" + wm.getPort()
        );
    }

    @Test
    @DisplayName("正常系: JSON レスポンスから RouteOption リストに変換できる")
    void 正常系_JSONレスポンスからRouteOptionリストに変換できる() {
        wm.stubFor(get(urlPathEqualTo("/route-options"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "viaLocodes": ["SGSIN", "JPTYO"],
                                    "transitDays": 14,
                                    "estimatedPrice": 150000,
                                    "voyageNumber": "SG001"
                                  },
                                  {
                                    "viaLocodes": ["SGSIN", "KRPUS", "JPTYO"],
                                    "transitDays": 18,
                                    "estimatedPrice": 120000,
                                    "voyageNumber": "SG002"
                                  }
                                ]
                                """)));

        List<RouteOption> result = adapter.findRouteOptions(anyCondition());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).voyageNumber()).isEqualTo("SG001");
        assertThat(result.get(0).transitDays()).isEqualTo(14);
        assertThat(result.get(1).voyageNumber()).isEqualTo("SG002");
        assertThat(result.get(1).transitDays()).isEqualTo(18);
    }

    @Test
    @DisplayName("異常系: HTTP 500 時に空リストを返す")
    void 異常系_HTTP500時に空リストを返す() {
        wm.stubFor(get(urlPathEqualTo("/route-options"))
                .willReturn(aResponse()
                        .withStatus(500)));

        List<RouteOption> result = adapter.findRouteOptions(anyCondition());

        assertThat(result).isEmpty();
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
