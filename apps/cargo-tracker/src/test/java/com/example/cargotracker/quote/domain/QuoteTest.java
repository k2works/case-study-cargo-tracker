package com.example.cargotracker.quote.domain;

import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;
import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import com.example.cargotracker.quote.domain.event.QuoteIssuedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Quote 集約")
class QuoteTest {

    private QuoteId anyQuoteId() {
        return QuoteId.generate();
    }

    private QuoteCondition anyCondition() {
        return new QuoteCondition(
                "JPTYO",
                "USNYC",
                LocalDate.of(2025, 9, 1),
                CargoType.GENERAL_CARGO,
                new BigDecimal("500.0")
        );
    }

    private List<RouteOption> anyRouteOptions() {
        return List.of(
                new RouteOption(
                        List.of("SGSIN"),
                        25,
                        new BigDecimal("150000"),
                        "V-2025-001"
                )
        );
    }

    @Nested
    @DisplayName("issue ファクトリメソッド")
    class IssueFactory {

        @Test
        @DisplayName("見積を正常に発行できる")
        void issueQuoteSuccessfully() {
            QuoteId id = anyQuoteId();
            QuoteCondition condition = anyCondition();
            List<RouteOption> options = anyRouteOptions();

            Quote quote = Quote.issue(id, condition, options);

            assertThat(quote.getId()).isEqualTo(id);
            assertThat(quote.getCondition()).isEqualTo(condition);
            assertThat(quote.getRouteOptions()).isEqualTo(options);
        }

        @Test
        @DisplayName("QuoteNumber が Q- プレフィクスで生成される")
        void quoteNumberStartsWithQPrefix() {
            Quote quote = Quote.issue(anyQuoteId(), anyCondition(), anyRouteOptions());

            assertThat(quote.getQuoteNumber().value()).startsWith("Q-");
        }

        @Test
        @DisplayName("QuoteNumber が Q-YYYYMMDD-XXXX 形式で生成される")
        void quoteNumberHasExpectedFormat() {
            Quote quote = Quote.issue(anyQuoteId(), anyCondition(), anyRouteOptions());

            // 例: Q-20250101-A3F9 のような形式
            assertThat(quote.getQuoteNumber().value()).matches("Q-\\d{8}-[A-Z0-9]{4}");
        }

        @Test
        @DisplayName("QuoteIssuedEvent が発行される")
        void issueEmitsQuoteIssuedEvent() {
            QuoteId id = anyQuoteId();

            Quote quote = Quote.issue(id, anyCondition(), anyRouteOptions());

            assertThat(quote.getDomainEvents()).hasSize(1);
            assertThat(quote.getDomainEvents().get(0)).isInstanceOf(QuoteIssuedEvent.class);

            QuoteIssuedEvent event = (QuoteIssuedEvent) quote.getDomainEvents().get(0);
            assertThat(event.quoteId()).isEqualTo(id);
            assertThat(event.quoteNumber()).isEqualTo(quote.getQuoteNumber());
        }

        @Test
        @DisplayName("id が null の場合は発行できない")
        void rejectNullId() {
            assertThatThrownBy(() -> Quote.issue(null, anyCondition(), anyRouteOptions()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("condition が null の場合は発行できない")
        void rejectNullCondition() {
            assertThatThrownBy(() -> Quote.issue(anyQuoteId(), null, anyRouteOptions()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("routeOptions が null の場合は発行できない")
        void rejectNullRouteOptions() {
            assertThatThrownBy(() -> Quote.issue(anyQuoteId(), anyCondition(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("routeOptions が空の場合は発行できない")
        void rejectEmptyRouteOptions() {
            assertThatThrownBy(() -> Quote.issue(anyQuoteId(), anyCondition(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("reconstitute ファクトリメソッド")
    class ReconstituteFactory {

        @Test
        @DisplayName("永続化ストアから再構成できる（ドメインイベントなし）")
        void reconstituteFromStore() {
            QuoteId id = anyQuoteId();
            QuoteCondition condition = anyCondition();
            List<RouteOption> options = anyRouteOptions();
            Quote original = Quote.issue(id, condition, options);

            Quote reconstituted = Quote.reconstitute(
                    id,
                    original.getQuoteNumber(),
                    condition,
                    options
            );

            assertThat(reconstituted.getId()).isEqualTo(id);
            assertThat(reconstituted.getQuoteNumber()).isEqualTo(original.getQuoteNumber());
            assertThat(reconstituted.getCondition()).isEqualTo(condition);
            assertThat(reconstituted.getRouteOptions()).isEqualTo(options);
            assertThat(reconstituted.getDomainEvents()).isEmpty();
        }
    }
}
