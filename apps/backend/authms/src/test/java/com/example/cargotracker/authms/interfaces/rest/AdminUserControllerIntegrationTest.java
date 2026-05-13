package com.example.cargotracker.authms.interfaces.rest;

import com.example.cargotracker.authms.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("local-h2")
@Transactional
@DisplayName("AdminUserController 統合テスト")
class AdminUserControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        adminToken = jwtTokenProvider.generateToken("admin", List.of("ROLE_ADMIN"));
        userToken = jwtTokenProvider.generateToken("shipper", List.of("ROLE_SHIPPER"));
    }

    // --- GET /api/v1/admin/users ---

    @Test
    @DisplayName("管理者が全ユーザー一覧を取得できる")
    void 管理者が全ユーザー一覧を取得できる() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].username").exists())
                .andExpect(jsonPath("$[0].roles").isArray());
    }

    @Test
    @DisplayName("一般ユーザーはユーザー一覧にアクセスできない")
    void 一般ユーザーはユーザー一覧にアクセスできない() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未認証ではユーザー一覧にアクセスできない")
    void 未認証ではユーザー一覧にアクセスできない() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /api/v1/admin/users ---

    @Test
    @DisplayName("管理者が新規ユーザーを作成できる")
    void 管理者が新規ユーザーを作成できる() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "newadminuser", "email": "newadminuser@example.com", "password": "password123", "role": "ROLE_SALES"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newadminuser"));
    }

    @Test
    @DisplayName("一般ユーザーは新規ユーザーを作成できない")
    void 一般ユーザーは新規ユーザーを作成できない() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "forbidden", "email": "forbidden@example.com", "password": "password123"}
                                """))
                .andExpect(status().isForbidden());
    }

    // --- PUT /api/v1/admin/users/{id}/roles ---

    @Test
    @DisplayName("管理者がユーザーのロールを更新できる")
    void 管理者がユーザーのロールを更新できる() throws Exception {
        // まず一覧を取得して admin 以外のユーザー ID を得る
        var result = mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // shipper の ID を抽出
        String userId = extractUserIdByUsername(body, "shipper");

        mockMvc.perform(put("/api/v1/admin/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["ROLE_SHIPPER", "ROLE_TRACKING"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ロールを更新しました"));
    }

    @Test
    @DisplayName("存在しないユーザーのロール更新は 404 を返す")
    void 存在しないユーザーのロール更新は404を返す() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/nonexistent-id/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["ROLE_ADMIN"]}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- PUT /api/v1/admin/users/{id}/status ---

    @Test
    @DisplayName("管理者がユーザーを無効化できる")
    void 管理者がユーザーを無効化できる() throws Exception {
        var result = mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String userId = extractUserIdByUsername(body, "shipper");

        mockMvc.perform(put("/api/v1/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ステータスを更新しました"));
    }

    @Test
    @DisplayName("存在しないユーザーのステータス更新は 404 を返す")
    void 存在しないユーザーのステータス更新は404を返す() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/nonexistent-id/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("一般ユーザーはステータス更新できない")
    void 一般ユーザーはステータス更新できない() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/some-id/status")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false}
                                """))
                .andExpect(status().isForbidden());
    }

    /**
     * JSON 配列文字列から指定ユーザー名の id を簡易抽出する。
     */
    private String extractUserIdByUsername(String jsonArray, String username) {
        // 簡易パース: "username":"shipper" の直前にある "id":"xxx" を取得
        int idx = jsonArray.indexOf("\"username\":\"" + username + "\"");
        if (idx < 0) {
            throw new AssertionError("ユーザー '" + username + "' が一覧に見つかりません");
        }
        // id は username より前にある
        String before = jsonArray.substring(0, idx);
        int idStart = before.lastIndexOf("\"id\":\"") + 6;
        int idEnd = before.indexOf("\"", idStart);
        return before.substring(idStart, idEnd);
    }
}
