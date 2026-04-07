package com.example.cargotracker.estimation.interfaces.web;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("EstimationController 統合テスト")
class EstimationControllerTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE route_candidate, estimate RESTART IDENTITY CASCADE");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser
    @DisplayName("GET /estimates で一覧画面がレンダリングされる")
    void getEstimates_shouldRenderIndexPage() throws Exception {
        mockMvc.perform(get("/estimates"))
                .andExpect(status().isOk())
                .andExpect(view().name("estimation/index"))
                .andExpect(content().string(containsString("見積管理")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /estimates/new で作成フォームがレンダリングされる")
    void getNewEstimate_shouldRenderFormPage() throws Exception {
        mockMvc.perform(get("/estimates/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("estimation/new"))
                .andExpect(content().string(containsString("見積作成")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /estimates で見積を登録後に詳細画面へリダイレクトする")
    void postEstimate_shouldRedirectToDetail() throws Exception {
        MvcResult result = mockMvc.perform(post("/estimates")
                        .with(csrf())
                        .param("originUnlocode", "JPTYO")
                        .param("destinationUnlocode", "USNYC")
                        .param("arrivalDeadline", LocalDate.now().plusDays(30).toString())
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1000.000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/estimates/")))
                .andExpect(flash().attributeExists("successMessage"))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(view().name("estimation/show"))
                .andExpect(content().string(containsString("ルート候補")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /estimates でバリデーションエラーがあると作成フォームに戻る")
    void postEstimate_withValidationError_shouldReturnToForm() throws Exception {
        mockMvc.perform(post("/estimates")
                        .with(csrf())
                        .param("originUnlocode", "")
                        .param("destinationUnlocode", "")
                        .param("arrivalDeadline", "")
                        .param("cargoType", "")
                        .param("weightKg", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("estimation/new"));
    }
}
