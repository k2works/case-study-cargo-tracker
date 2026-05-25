package com.example.bookingms.domain.model;

import com.example.bookingms.domain.commands.CreateQuotationCommand;
import com.example.bookingms.domain.events.QuotationCreatedEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

class QuotationAggregateTest {

    private FixtureConfiguration<Quotation> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Quotation.class);
    }

    private CargoSpecification generalSpec() {
        return new CargoSpecification(
                CargoType.GENERAL,
                new BigDecimal("1500.00"),
                new Dimensions(120, 80, 60),
                10,
                "電子部品");
    }

    private RouteSpecification routeSpec() {
        return new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30));
    }

    private CreateQuotationCommand validCommand(String quotationId) {
        return new CreateQuotationCommand(
                quotationId,
                "S-001",
                routeSpec(),
                generalSpec(),
                List.of(new RouteCandidate("JPTYO → USNYC（直行）", 14, new BigDecimal("850000"), "JPY")),
                new BigDecimal("850000"),
                "JPY",
                LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("US01: 輸送見積を作成できる")
    void 輸送見積を作成できる() {
        CreateQuotationCommand cmd = validCommand("Q-001");
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new QuotationCreatedEvent(
                        "Q-001", "S-001", cmd.routeSpec(), cmd.cargoSpec(),
                        cmd.candidateRoutes(), cmd.estimatedAmount(), "JPY",
                        LocalDate.of(2026, 8, 31), "DRAFT"));
    }

    @Test
    @DisplayName("US01: 期限内ルートが無い場合も候補なしで見積を作成できる")
    void 候補なしでも見積を作成できる() {
        CreateQuotationCommand cmd = new CreateQuotationCommand(
                "Q-002", "S-001", routeSpec(), generalSpec(),
                List.of(), null, null, LocalDate.of(2026, 8, 31));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new QuotationCreatedEvent(
                        "Q-002", "S-001", cmd.routeSpec(), cmd.cargoSpec(),
                        List.of(), null, null, LocalDate.of(2026, 8, 31), "DRAFT"));
    }

    @Test
    @DisplayName("US01: 見積 ID が空文字列の場合は例外")
    void 見積IDが空文字列の場合は例外() {
        fixture.givenNoPriorActivity()
                .when(validCommand(""))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US01: 荷主 ID が空文字列の場合は例外")
    void 荷主IDが空文字列の場合は例外() {
        CreateQuotationCommand cmd = new CreateQuotationCommand(
                "Q-003", "", routeSpec(), generalSpec(),
                List.of(), new BigDecimal("850000"), "JPY", LocalDate.of(2026, 8, 31));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US01: 出発地と目的地が同一の場合は例外")
    void 出発地と目的地が同一の場合は例外() {
        CreateQuotationCommand cmd = new CreateQuotationCommand(
                "Q-004", "S-001",
                new RouteSpecification("JPTYO", "JPTYO", LocalDate.of(2026, 9, 30)),
                generalSpec(), List.of(), new BigDecimal("850000"), "JPY",
                LocalDate.of(2026, 8, 31));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US01: 見積有効期限が過去日の場合は例外")
    void 見積有効期限が過去日の場合は例外() {
        CreateQuotationCommand cmd = new CreateQuotationCommand(
                "Q-005", "S-001", routeSpec(), generalSpec(),
                List.of(), new BigDecimal("850000"), "JPY", LocalDate.of(2020, 1, 1));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }
}
