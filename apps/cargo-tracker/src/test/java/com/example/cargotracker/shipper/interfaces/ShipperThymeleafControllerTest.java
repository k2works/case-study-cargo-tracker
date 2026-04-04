package com.example.cargotracker.shipper.interfaces;

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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin"
})
@ActiveProfiles("test")
@DisplayName("Shipper Thymeleaf Controller 統合テスト")
class ShipperThymeleafControllerTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM shipper");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser
    @DisplayName("GET /shippers で一覧画面がレンダリングされる")
    void getShippers_shouldRenderIndexPage() throws Exception {
        mockMvc.perform(get("/shippers"))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/index"))
                .andExpect(content().string(containsString("荷主管理")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /shippers/new で登録フォームがレンダリングされる")
    void getNewShipper_shouldRenderFormPage() throws Exception {
        mockMvc.perform(get("/shippers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/new"))
                .andExpect(content().string(containsString("荷主登録")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /shippers で登録後に詳細画面へリダイレクトする")
    void postShipper_shouldRedirectToShowPage() throws Exception {
        MvcResult result = mockMvc.perform(post("/shippers")
                        .with(csrf())
                        .param("name", "画面テスト株式会社")
                        .param("email", "web@example.com")
                        .param("phone", "03-9999-9999")
                        .param("shipperType", "CORPORATE")
                        .param("contractNumber", "CN-WEB")
                        .param("discountRate", "0.08"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/shippers/")))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/show"))
                .andExpect(content().string(containsString("画面テスト株式会社")));
    }
}
