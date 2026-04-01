package com.example.cargotracker.quote.application.internal.commandservices;

import com.example.cargotracker.quote.application.internal.outboundservices.QuoteRouteProviderPort;
import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import com.example.cargotracker.quote.domain.repository.QuoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterQuoteCommandService")
class RegisterQuoteCommandServiceTest {

    @Mock
    private QuoteRouteProviderPort quoteRouteProviderPort;

    @Mock
    private QuoteRepository quoteRepository;

    @InjectMocks
    private RegisterQuoteCommandService service;

    @Test
    @DisplayName("ルート候補が取得できた場合は見積を発行・保存して返す")
    void register_正常見積発行() {
        RouteOption route = new RouteOption(
                List.of("SGSIN"),
                14,
                new BigDecimal("150000"),
                "SG001"
        );
        when(quoteRouteProviderPort.findRouteOptions(any())).thenReturn(List.of(route));

        RegisterQuoteCommand command = new RegisterQuoteCommand(
                "JPTYO",
                "USNYC",
                LocalDate.of(2025, 12, 1),
                CargoType.GENERAL_CARGO,
                new BigDecimal("1000")
        );

        Quote result = service.register(command);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getQuoteNumber().value()).startsWith("Q-");
        assertThat(result.getCondition().originLocode()).isEqualTo("JPTYO");
        assertThat(result.getCondition().destinationLocode()).isEqualTo("USNYC");
        assertThat(result.getRouteOptions()).hasSize(1);
        verify(quoteRepository).save(result);
    }

    @Test
    @DisplayName("ルート候補が 0 件の場合は NoRouteAvailableException をスローする")
    void register_ルート候補なしで例外() {
        when(quoteRouteProviderPort.findRouteOptions(any())).thenReturn(List.of());

        RegisterQuoteCommand command = new RegisterQuoteCommand(
                "JPTYO",
                "USNYC",
                LocalDate.of(2025, 12, 1),
                CargoType.GENERAL_CARGO,
                new BigDecimal("1000")
        );

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(NoRouteAvailableException.class)
                .hasMessageContaining("JPTYO")
                .hasMessageContaining("USNYC");
    }
}
