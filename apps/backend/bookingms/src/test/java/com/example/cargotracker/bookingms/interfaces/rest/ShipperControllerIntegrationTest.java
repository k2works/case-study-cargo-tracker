package com.example.cargotracker.bookingms.interfaces.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
@DisplayName("ShipperController 統合テスト")
class ShipperControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("POST /api/v1/shippers で個人荷主を登録できる（201）")
    void 個人荷主を登録できる() throws Exception {
        mockMvc.perform(post("/api/v1/shippers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "田中太郎",
                                    "email": "tanaka@example.com",
                                    "phone": "090-1234-5678",
                                    "shipperType": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("田中太郎"))
                .andExpect(jsonPath("$.shipperType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.shipperCode").isNotEmpty())
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("POST /api/v1/shippers で法人荷主を登録できる（201）")
    void 法人荷主を登録できる() throws Exception {
        mockMvc.perform(post("/api/v1/shippers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "株式会社テスト",
                                    "email": "corp@example.com",
                                    "phone": "03-1234-5678",
                                    "shipperType": "CORPORATE",
                                    "contractNumber": "CNT-001",
                                    "discountRate": 0.10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("株式会社テスト"))
                .andExpect(jsonPath("$.shipperType").value("CORPORATE"))
                .andExpect(jsonPath("$.contractNumber").value("CNT-001"));
    }

    @Test
    @DisplayName("GET /api/v1/shippers で一覧を取得できる（200）")
    void 一覧を取得できる() throws Exception {
        // まず登録
        mockMvc.perform(post("/api/v1/shippers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"テスト","email":"list@example.com","phone":"000","shipperType":"INDIVIDUAL"}
                        """));

        mockMvc.perform(get("/api/v1/shippers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("テスト"));
    }

    @Test
    @DisplayName("重複メールアドレスで 409")
    void 重複メールアドレスで409() throws Exception {
        String body = """
                {"name":"テスト","email":"dup@example.com","phone":"000","shipperType":"INDIVIDUAL"}
                """;
        mockMvc.perform(post("/api/v1/shippers")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/shippers")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("割引率が範囲外で 400")
    void 割引率が範囲外で400() throws Exception {
        mockMvc.perform(post("/api/v1/shippers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "株式会社テスト",
                                    "email": "bad@example.com",
                                    "phone": "03-0000-0000",
                                    "shipperType": "CORPORATE",
                                    "contractNumber": "CNT-001",
                                    "discountRate": 0.50
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("name が空の場合バリデーションエラー 400")
    void nameが空の場合バリデーションエラー400() throws Exception {
        mockMvc.perform(post("/api/v1/shippers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "",
                                    "email": "valid@example.com",
                                    "shipperType": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
