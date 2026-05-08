package com.example.authms.interfaces.rest;

import com.example.authms.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthController authController;

    @Test
    void 正しい認証情報でログインできる() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "admin", "password": "password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void 誤ったパスワードで認証に失敗する() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "admin", "password": "wrongpassword"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void 存在しないユーザーで認証に失敗する() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "nonexistent", "password": "password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void 認証済みユーザーが自分の情報を取得できる() throws Exception {
        String token = jwtTokenProvider.generateToken("admin", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void 未認証でmeAPIにアクセスすると401を返す() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 新規ユーザーを登録できる() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "newuser", "email": "newuser@example.com", "password": "password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"));
    }

    @Test
    void 重複するユーザー名で登録に失敗する() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "admin", "email": "another@example.com", "password": "password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void roleを指定してユーザー登録できる() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "shipper2", "email": "shipper2@example.com", "password": "password123", "role": "ROLE_SHIPPER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("shipper2"));
    }

    @Test
    void 存在しないユーザーのmeは404を返す() throws Exception {
        String token = jwtTokenProvider.generateToken("ghost-user", List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("ユーザーが見つかりません"));
    }

    @Test
    void meで認証情報がnullなら401レスポンスを返す() {
        var response = authController.me(null);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        org.assertj.core.api.Assertions.assertThat(response.getBody())
                .isEqualTo(java.util.Map.of("message", "認証が必要です"));
    }
}
