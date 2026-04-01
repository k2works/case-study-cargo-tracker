package com.example.cargotracker.quote.interfaces.web;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(QuoteWebController.class)
@WithMockUser
@DisplayName("QuoteWebController")
class QuoteWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterQuoteCommandService registerQuoteCommandService;

    @MockitoBean
    private FindQuoteQueryService findQuoteQueryService;

    // ── GET /quotes/new ────────────────────────────────────────────────────

    @Test
    @DisplayName("見積登録フォームを表示できる")
    void showRegisterForm() throws Exception {
        mockMvc.perform(get("/quotes/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("quote/register"))
                .andExpect(model().attributeExists("form"))
                .andExpect(model().attributeExists("cargoTypes"));
    }

    // ── POST /quotes ───────────────────────────────────────────────────────

    @Test
    @DisplayName("正常登録時は見積詳細へリダイレクトする")
    void register_正常登録() throws Exception {
        Quote quote = anyQuote();
        when(registerQuoteCommandService.register(any())).thenReturn(quote);

        mockMvc.perform(post("/quotes")
                        .param("originLocode", "JPTYO")
                        .param("destinationLocode", "USNYC")
                        .param("requestedArrivalDate", "2025-12-01")
                        .param("cargoType", "GENERAL_CARGO")
                        .param("weightKg", "1000.0")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quotes/" + quote.getId().value()));
    }

    @Test
    @DisplayName("バリデーションエラーの場合は登録フォームに戻る")
    void register_バリデーションエラー() throws Exception {
        mockMvc.perform(post("/quotes")
                        .param("originLocode", "")  // 必須項目が空
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("quote/register"))
                .andExpect(model().attributeHasErrors("form"))
                .andExpect(model().attributeExists("cargoTypes"));
    }

    @Test
    @DisplayName("ルート候補なし時はエラーメッセージをセットしてフォームに戻る")
    void register_ルートなし() throws Exception {
        when(registerQuoteCommandService.register(any()))
                .thenThrow(new NoRouteAvailableException("JPTYO", "USNYC"));

        mockMvc.perform(post("/quotes")
                        .param("originLocode", "JPTYO")
                        .param("destinationLocode", "USNYC")
                        .param("requestedArrivalDate", "2025-12-01")
                        .param("cargoType", "GENERAL_CARGO")
                        .param("weightKg", "1000.0")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("quote/register"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attributeExists("cargoTypes"));
    }

    // ── GET /quotes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("見積一覧を表示できる")
    void showList() throws Exception {
        when(findQuoteQueryService.findAll()).thenReturn(List.of(anyQuote()));

        mockMvc.perform(get("/quotes"))
                .andExpect(status().isOk())
                .andExpect(view().name("quote/list"))
                .andExpect(model().attributeExists("quotes"));
    }

    // ── GET /quotes/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("見積詳細を表示できる")
    void showDetail() throws Exception {
        Quote quote = anyQuote();
        when(findQuoteQueryService.findById(quote.getId())).thenReturn(quote);

        mockMvc.perform(get("/quotes/" + quote.getId().value()))
                .andExpect(status().isOk())
                .andExpect(view().name("quote/detail"))
                .andExpect(model().attributeExists("quote"));
    }

    @Test
    @DisplayName("存在しない見積 ID を指定した場合は 404 を返す")
    void showDetail_存在しない() throws Exception {
        QuoteId quoteId = QuoteId.generate();
        when(findQuoteQueryService.findById(quoteId))
                .thenThrow(new QuoteNotFoundException(quoteId.value().toString()));

        mockMvc.perform(get("/quotes/" + quoteId.value()))
                .andExpect(status().isNotFound());
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
