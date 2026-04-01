package com.example.cargotracker.quote.interfaces.web;

import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.repository.QuoteRepository;
import com.example.cargotracker.quote.application.internal.commandservices.RegisterQuoteCommand;
import com.example.cargotracker.quote.application.internal.commandservices.RegisterQuoteCommandService;
import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 見積作成の統合テスト（Web レイヤー → サービス → DB）。
 * StubQuoteRouteProviderAdapter と PostgreSQL Testcontainers を使用して
 * エンドツーエンドのフローを検証する。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("見積作成 統合テスト")
class QuoteRegisterIntegrationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RegisterQuoteCommandService registerQuoteCommandService;

    @Autowired
    private QuoteRepository quoteRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // ── サービス層統合テスト ────────────────────────────────────────────────

    @Test
    @DisplayName("StubRouteProvider 経由で見積を作成し DB に保存できる")
    @Transactional
    void register_StubRoute経由でDB保存() {
        RegisterQuoteCommand command = new RegisterQuoteCommand(
                "JPTYO",
                "USNYC",
                LocalDate.of(2025, 12, 1),
                CargoType.GENERAL_CARGO,
                new BigDecimal("1000.00")
        );

        Quote quote = registerQuoteCommandService.register(command);

        assertThat(quote).isNotNull();
        assertThat(quote.getId()).isNotNull();
        assertThat(quote.getQuoteNumber().value()).startsWith("Q-");
        assertThat(quote.getRouteOptions()).isNotEmpty();

        // DB に永続化されていることを確認
        assertThat(quoteRepository.findById(quote.getId())).isPresent();
    }

    @Test
    @DisplayName("StubRouteProvider は複数のルート候補を返す")
    @Transactional
    void register_複数ルート候補が取得される() {
        RegisterQuoteCommand command = new RegisterQuoteCommand(
                "JPTYO",
                "USNYC",
                LocalDate.of(2025, 12, 1),
                CargoType.GENERAL_CARGO,
                new BigDecimal("1000.00")
        );

        Quote quote = registerQuoteCommandService.register(command);

        assertThat(quote.getRouteOptions()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(quote.getRouteOptions()).allMatch(r -> r.transitDays() > 0);
        assertThat(quote.getRouteOptions()).allMatch(r -> r.estimatedPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    // ── Web レイヤー統合テスト ──────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /quotes/new は見積登録フォームを表示する")
    void showRegisterForm_フォームが表示される() throws Exception {
        mockMvc.perform(get("/quotes/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("quote/register"))
                .andExpect(model().attributeExists("form"))
                .andExpect(model().attributeExists("cargoTypes"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /quotes で見積を作成し詳細ページへリダイレクトされる")
    void register_正常系_詳細へリダイレクト() throws Exception {
        mockMvc.perform(post("/quotes")
                        .param("originLocode", "JPTYO")
                        .param("destinationLocode", "USNYC")
                        .param("requestedArrivalDate", "2025-12-01")
                        .param("cargoType", "GENERAL_CARGO")
                        .param("weightKg", "1000.0")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/quotes/*"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /quotes でバリデーションエラー時はフォームに戻る")
    void register_バリデーションエラー_フォームに戻る() throws Exception {
        mockMvc.perform(post("/quotes")
                        .param("originLocode", "")
                        .param("destinationLocode", "USNYC")
                        .param("requestedArrivalDate", "2025-12-01")
                        .param("cargoType", "GENERAL_CARGO")
                        .param("weightKg", "1000.0")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("quote/register"))
                .andExpect(model().attributeHasErrors("form"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /quotes で登録後に詳細ページにアクセスするとルート候補が表示される")
    void register_詳細ページでルート候補表示() throws Exception {
        // 見積を作成
        RegisterQuoteCommand command = new RegisterQuoteCommand(
                "JPTYO",
                "USNYC",
                LocalDate.of(2025, 12, 1),
                CargoType.GENERAL_CARGO,
                new BigDecimal("1000.00")
        );
        Quote quote = registerQuoteCommandService.register(command);

        // 詳細ページを取得
        mockMvc.perform(get("/quotes/" + quote.getId().value()))
                .andExpect(status().isOk())
                .andExpect(view().name("quote/detail"))
                .andExpect(model().attributeExists("quote"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /quotes は見積一覧を表示する")
    void showList_一覧が表示される() throws Exception {
        mockMvc.perform(get("/quotes"))
                .andExpect(status().isOk())
                .andExpect(view().name("quote/list"))
                .andExpect(model().attributeExists("quotes"));
    }
}
