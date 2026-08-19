package com.example.authms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.authms.application.internal.LoginResult;
import com.example.authms.application.internal.LoginUseCase;
import com.example.authms.application.port.AuthAuditLogger;
import com.example.authms.domain.model.AuthEventType;
import com.example.authms.domain.model.Role;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@DisplayName("認証 API")
class AuthControllerTest {

    private static final String FAILURE_MESSAGE = "利用者 ID またはパスワードが正しくありません";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoginUseCase loginUseCase;

    @MockitoBean
    private AuthAuditLogger auditLogger;

    @Nested
    @DisplayName("ログイン")
    class Login {

        @Test
        @DisplayName("成功したらトークンと表示名とロールを返す")
        void returnsToken() throws Exception {
            when(loginUseCase.login("sales01", "password"))
                    .thenReturn(Optional.of(new LoginResult(
                            "jwt-token", "sales01", "山田太郎", Set.of(Role.ROLE_SALES))));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId": "sales01", "password": "password"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token"))
                    .andExpect(jsonPath("$.displayName").value("山田太郎"))
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_SALES"));
        }

        @Test
        @DisplayName("失敗したら 401 と同一の文言を返す")
        void returnsUniformFailure() throws Exception {
            when(loginUseCase.login(any(), any())).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId": "sales01", "password": "wrong"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(FAILURE_MESSAGE));
        }

        @Test
        @DisplayName("失敗の理由を応答に含めない")
        void doesNotLeakFailureReason() throws Exception {
            when(loginUseCase.login(any(), any())).thenReturn(Optional.empty());

            // 「ロック中」「無効」等が読み取れると、その利用者 ID が存在することが分かる（US31）
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId": "locked01", "password": "password"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(FAILURE_MESSAGE))
                    .andExpect(jsonPath("$.reason").doesNotExist())
                    .andExpect(jsonPath("$.lockedUntil").doesNotExist());
        }

        @Test
        @DisplayName("利用者 ID が空なら 400 で返す")
        void rejectsBlankUserId() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId": "", "password": "password"}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("認証済み利用者の情報")
    class Me {

        @Test
        @DisplayName("Gateway が付けた検証済みクレームをそのまま返す")
        void readsVerifiedClaims() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("X-Authenticated-User-Id", "sales01")
                            .header("X-Authenticated-Roles", "ROLE_SALES,ROLE_TRACKER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("sales01"))
                    .andExpect(jsonPath("$.roles[1]").value("ROLE_TRACKER"));
        }

        @Test
        @DisplayName("クレームが無ければ 400 で返す（Gateway を通っていない呼び出し）")
        void rejectsRequestWithoutClaims() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("ログアウト")
    class Logout {

        @Test
        @DisplayName("監査ログに残して 204 を返す")
        void recordsLogout() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout").header("X-Authenticated-User-Id", "sales01"))
                    .andExpect(status().isNoContent());

            verify(auditLogger).record(eq("sales01"), eq(AuthEventType.LOGOUT), any());
        }
    }
}
