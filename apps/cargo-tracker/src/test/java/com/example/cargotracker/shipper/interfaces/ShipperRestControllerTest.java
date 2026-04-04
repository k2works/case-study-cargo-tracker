package com.example.cargotracker.shipper.interfaces;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin"
})
@ActiveProfiles("test")
@DisplayName("Shipper REST Controller 統合テスト")
class ShipperRestControllerTest extends PostgreSQLIntegrationTestBase {

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
    @DisplayName("POST /api/v1/shippers で個人荷主を登録すると 201 と ID が返る")
    void postRegisterShipper_shouldReturnCreated() throws Exception {
        mockMvc.perform(post("/api/v1/shippers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "山田 太郎",
                                  "email": "taro@example.com",
                                  "phone": "090-1234-5678",
                                  "shipperType": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/shippers で重複メールは 409 が返る")
    void postRegisterShipper_withDuplicatedEmail_shouldReturnConflict() throws Exception {
        String requestBody = """
                {
                  "name": "山田 太郎",
                  "email": "duplicate@example.com",
                  "phone": "090-1234-5678",
                  "shipperType": "INDIVIDUAL"
                }
                """;

        mockMvc.perform(post("/api/v1/shippers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/shippers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("EMAIL_ALREADY_REGISTERED")))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/shippers/{id} で登録済み荷主が取得できる")
    void getShipperById_shouldReturnRegisteredShipper() throws Exception {
        String shipperId = registerShipper("""
                {
                  "name": "株式会社テスト",
                  "email": "corp@example.com",
                  "phone": "03-1234-5678",
                  "shipperType": "CORPORATE",
                  "contractNumber": "CN-001",
                  "discountRate": 0.10
                }
                """);

        mockMvc.perform(get("/api/v1/shippers/{id}", shipperId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(shipperId)))
                .andExpect(jsonPath("$.name", is("株式会社テスト")))
                .andExpect(jsonPath("$.email", is("corp@example.com")))
                .andExpect(jsonPath("$.shipperType", is("CORPORATE")))
                .andExpect(jsonPath("$.contractNumber", is("CN-001")))
                .andExpect(jsonPath("$.discountRate", is(0.10)));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/shippers/{id} で存在しない ID は 404 が返る")
    void getShipperById_withUnknownId_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/shippers/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/shippers で荷主一覧が取得できる")
    void getShippers_shouldReturnRegisteredShippers() throws Exception {
        registerShipper("""
                {
                  "name": "山田 太郎",
                  "email": "list1@example.com",
                  "phone": "090-0000-0001",
                  "shipperType": "INDIVIDUAL"
                }
                """);
        registerShipper("""
                {
                  "name": "株式会社一覧",
                  "email": "list2@example.com",
                  "phone": "03-0000-0002",
                  "shipperType": "CORPORATE",
                  "contractNumber": "CN-002",
                  "discountRate": 0.05
                }
                """);

        mockMvc.perform(get("/api/v1/shippers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("認証なしで POST /api/v1/shippers すると 401 が返る")
    void postRegisterShipper_withoutAuthentication_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/shippers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "未認証ユーザー",
                                  "email": "guest@example.com",
                                  "shipperType": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    private String registerShipper(String requestBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/shippers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        return content.replaceFirst("^.*\"id\"\\s*:\\s*\"([^\"]+)\".*$", "$1");
    }
}
