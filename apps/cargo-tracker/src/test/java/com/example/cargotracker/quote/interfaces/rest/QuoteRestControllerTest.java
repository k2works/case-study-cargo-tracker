package com.example.cargotracker.quote.interfaces.rest;

import com.example.cargotracker.quote.application.internal.commandservices.NoRouteAvailableException;
import com.example.cargotracker.quote.application.internal.commandservices.RegisterQuoteCommandService;
import com.example.cargotracker.quote.application.internal.queryservices.FindQuoteQueryService;
import com.example.cargotracker.quote.application.internal.queryservices.QuoteNotFoundException;
import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;
import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuoteRestController.class)
@WithMockUser
@DisplayName("QuoteRestController")
class QuoteRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterQuoteCommandService registerQuoteCommandService;

    @MockitoBean
    private FindQuoteQueryService findQuoteQueryService;

    @Test
    @DisplayName("見積一覧を JSON で取得できる")
    void list() throws Exception {
        Quote quote = anyQuote();
        when(findQuoteQueryService.findAll()).thenReturn(List.of(quote));

        mockMvc.perform(get("/api/quotes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(quote.getId().value().toString()))
                .andExpect(jsonPath("$[0].quoteNumber").value(quote.getQuoteNumber().value()))
                .andExpect(jsonPath("$[0].condition.originLocode").value("JPTYO"));
    }

    @Test
    @DisplayName("見積詳細を JSON で取得できる")
    void detail() throws Exception {
        Quote quote = anyQuote();
        when(findQuoteQueryService.findById(quote.getId())).thenReturn(quote);

        mockMvc.perform(get("/api/quotes/" + quote.getId().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(quote.getId().value().toString()))
                .andExpect(jsonPath("$.condition.destinationLocode").value("USNYC"));
    }

    @Test
    @DisplayName("存在しない見積は 404 を返す")
    void detail_存在しない() throws Exception {
        QuoteId quoteId = QuoteId.generate();
        when(findQuoteQueryService.findById(any()))
                .thenThrow(new QuoteNotFoundException(quoteId.value().toString()));

        mockMvc.perform(get("/api/quotes/" + quoteId.value()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("見積が見つかりません: " + quoteId.value()));
    }

    @Test
    @DisplayName("見積登録 API は 201 と Location を返す")
    void register() throws Exception {
        Quote quote = anyQuote();
        when(registerQuoteCommandService.register(any())).thenReturn(quote);

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originLocode": "JPTYO",
                                  "destinationLocode": "USNYC",
                                  "requestedArrivalDate": "2025-12-01",
                                  "cargoType": "GENERAL_CARGO",
                                  "weightKg": 1000.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/api/quotes/" + quote.getId().value()))
                .andExpect(jsonPath("$.id").value(quote.getId().value().toString()));
    }

    @Test
    @DisplayName("ルート候補なし時は 422 を返す")
    void register_ルートなし() throws Exception {
        when(registerQuoteCommandService.register(any()))
                .thenThrow(new NoRouteAvailableException("JPTYO", "USNYC"));

        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originLocode": "JPTYO",
                                  "destinationLocode": "USNYC",
                                  "requestedArrivalDate": "2025-12-01",
                                  "cargoType": "GENERAL_CARGO",
                                  "weightKg": 1000.0
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("利用可能なルートが見つかりません: JPTYO → USNYC"));
    }

    private Quote anyQuote() {
        QuoteId id = QuoteId.generate();
        QuoteCondition condition = new QuoteCondition(
                "JPTYO", "USNYC",
                LocalDate.of(2025, 12, 1),
                CargoType.GENERAL_CARGO,
                new BigDecimal("1000")
        );
        RouteOption route = new RouteOption(
                List.of("SGSIN"), 14, new BigDecimal("150000"), "SG001"
        );
        return Quote.issue(id, condition, List.of(route));
    }
}
