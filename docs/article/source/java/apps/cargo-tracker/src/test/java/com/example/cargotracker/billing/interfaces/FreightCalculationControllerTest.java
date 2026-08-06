package com.example.cargotracker.billing.interfaces;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin"
})
@ActiveProfiles("test")
@DisplayName("FreightCalculation Thymeleaf Controller 統合テスト")
class FreightCalculationControllerTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE invoice RESTART IDENTITY CASCADE");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser
    @DisplayName("GET /billing/freight-calculation で算出フォームがレンダリングされる")
    void getFreightCalculation_shouldRenderForm() throws Exception {
        mockMvc.perform(get("/billing/freight-calculation"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/freight-calculation"))
                .andExpect(content().string(containsString("輸送料金算出")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /billing/freight-calculation?bookingId=存在しないID では請求書なしメッセージを表示する")
    void getFreightCalculation_withUnknownBookingId_shouldShowNoInvoiceMessage() throws Exception {
        mockMvc.perform(get("/billing/freight-calculation").param("bookingId", "BK-UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(view().name("billing/freight-calculation"))
                .andExpect(content().string(containsString("請求書が見つかりませんでした")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /billing/freight-calculation で存在しない予約IDはエラーリダイレクトする")
    void postFreightCalculation_withUnknownBookingId_shouldRedirectWithError() throws Exception {
        mockMvc.perform(post("/billing/freight-calculation")
                        .with(csrf())
                        .param("bookingId", "BK-UNKNOWN")
                        .param("adjustmentAmount", "0")
                        .param("adjustmentReason", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/billing/freight-calculation")));
    }
}
