package com.example.cargotracker.quote.infrastructure;

import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;
import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import com.example.cargotracker.quote.domain.repository.QuoteRepository;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("QuoteRepository 統合テスト")
class QuoteRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private QuoteRepository quoteRepository;

    private QuoteCondition anyCondition() {
        return new QuoteCondition(
                "JPTYO",
                "USNYC",
                LocalDate.of(2025, 10, 1),
                CargoType.GENERAL_CARGO,
                new BigDecimal("500.00")
        );
    }

    private List<RouteOption> anyRouteOptions() {
        return List.of(
                new RouteOption(
                        List.of("SGSIN", "HKHKG"),
                        21,
                        new BigDecimal("150000.00"),
                        "V-001"
                ),
                new RouteOption(
                        List.of("CNSHA"),
                        14,
                        new BigDecimal("180000.00"),
                        "V-002"
                )
        );
    }

    @Test
    @DisplayName("見積を保存して ID で取得できる")
    void saveAndFindById() {
        QuoteId id = QuoteId.generate();
        Quote quote = Quote.issue(id, anyCondition(), anyRouteOptions());

        quoteRepository.save(quote);

        Optional<Quote> found = quoteRepository.findById(id);
        assertThat(found).isPresent();

        Quote actual = found.get();
        assertThat(actual.getId()).isEqualTo(id);
        assertThat(actual.getQuoteNumber().value()).startsWith("Q-");
        assertThat(actual.getCondition().originLocode()).isEqualTo("JPTYO");
        assertThat(actual.getCondition().destinationLocode()).isEqualTo("USNYC");
        assertThat(actual.getCondition().requestedArrivalDate()).isEqualTo(LocalDate.of(2025, 10, 1));
        assertThat(actual.getCondition().cargoType()).isEqualTo(CargoType.GENERAL_CARGO);
        assertThat(actual.getCondition().weightKg()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("ルート候補が正しく永続化・再構成される")
    void routeOptionsArePersistedAndReconstituted() {
        QuoteId id = QuoteId.generate();
        Quote quote = Quote.issue(id, anyCondition(), anyRouteOptions());

        quoteRepository.save(quote);

        Quote found = quoteRepository.findById(id).orElseThrow();
        List<RouteOption> options = found.getRouteOptions();

        assertThat(options).hasSize(2);

        RouteOption first = options.get(0);
        assertThat(first.voyageNumber()).isEqualTo("V-001");
        assertThat(first.transitDays()).isEqualTo(21);
        assertThat(first.estimatedPrice()).isEqualByComparingTo("150000.00");
        assertThat(first.viaLocodes()).containsExactly("SGSIN", "HKHKG");

        RouteOption second = options.get(1);
        assertThat(second.voyageNumber()).isEqualTo("V-002");
        assertThat(second.transitDays()).isEqualTo(14);
        assertThat(second.estimatedPrice()).isEqualByComparingTo("180000.00");
        assertThat(second.viaLocodes()).containsExactly("CNSHA");
    }

    @Test
    @DisplayName("存在しない ID の場合は空の Optional を返す")
    void findByIdNotFound() {
        Optional<Quote> found = quoteRepository.findById(QuoteId.generate());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("再取得した見積はドメインイベントを持たない")
    void reconstitutedQuoteShouldNotHaveDomainEvents() {
        QuoteId id = QuoteId.generate();
        Quote quote = Quote.issue(id, anyCondition(), anyRouteOptions());
        quoteRepository.save(quote);

        Quote found = quoteRepository.findById(id).orElseThrow();
        assertThat(found.getDomainEvents()).isEmpty();
    }
}
