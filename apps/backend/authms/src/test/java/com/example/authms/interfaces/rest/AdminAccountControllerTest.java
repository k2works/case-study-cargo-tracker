package com.example.authms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.authms.application.internal.commandservices.UnlockAccountUseCase;
import com.example.authms.domain.model.LoginState;
import com.example.authms.domain.model.User;
import com.example.authms.domain.model.UserIdentity;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** ロックされたアカウントの管理 API（US32）。 */
@WebMvcTest(AdminAccountController.class)
@DisplayName("アカウント管理 API")
class AdminAccountControllerTest {

    private static final Instant LOCKED_UNTIL = Instant.parse("2026-08-22T02:15:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UnlockAccountUseCase unlockAccount;

    private static User locked() {
        return User.restore(1L,
                new UserIdentity("sales01", "sales01@example.com", "山田太郎", "hash"),
                true, new LoginState(5, LOCKED_UNTIL), Set.of(Role.ROLE_SALES));
    }

    @Test
    @DisplayName("管理者はロック中のアカウントを一覧できる")
    void listsLockedAccounts() throws Exception {
        when(unlockAccount.lockedAccounts()).thenReturn(List.of(locked()));

        mockMvc.perform(get("/api/v1/admin/accounts/locked")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("sales01"))
                .andExpect(jsonPath("$[0].displayName").value("山田太郎"))
                .andExpect(jsonPath("$[0].failedAttempts").value(5))
                .andExpect(jsonPath("$[0].lockedUntil").exists());
    }

    /**
     * <strong>要らないものを返さない。</strong>
     *
     * <p>管理者が解除の判断をするのに要るのは「誰が・いつまでロックされているか」だけである。
     * パスワードのハッシュやメールアドレスを返すと、画面の不具合や記録の流出でそのまま漏れる。
     */
    @Test
    @DisplayName("パスワードのハッシュもメールアドレスも返さない")
    void doesNotLeakCredentials() throws Exception {
        when(unlockAccount.lockedAccounts()).thenReturn(List.of(locked()));

        mockMvc.perform(get("/api/v1/admin/accounts/locked")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    @DisplayName("管理者は解除でき、解除した本人が記録に渡る")
    void unlocksAndRecordsTheActor() throws Exception {
        when(unlockAccount.unlock(any(), any())).thenReturn(Optional.of(locked().unlock()));

        mockMvc.perform(post("/api/v1/admin/accounts/sales01/unlock")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedUntil").doesNotExist());

        // 記録に残すのは「誰が解除したか」である。対象と取り違えると監査に答えられない
        verify(unlockAccount).unlock("sales01", "admin01");
    }

    /**
     * US32-4。<strong>管理者以外は 403</strong>。
     *
     * <p>他のロールに開くと、誰でも他人のロックを外せることになり、アカウント保護（US31）が
     * 意味を失う。
     */
    @Test
    @DisplayName("管理者以外は一覧も解除もできない（403）")
    void refusesEveryOtherRole() throws Exception {
        for (String role : new String[] {
            "ROLE_SALES", "ROLE_ROUTING", "ROLE_TRACKER", "ROLE_HANDLER",
            "ROLE_ACCOUNTANT", "ROLE_SHIPPER",
        }) {
            mockMvc.perform(get("/api/v1/admin/accounts/locked")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/v1/admin/accounts/sales01/unlock")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role))
                    .andExpect(status().isForbidden());
        }

        verify(unlockAccount, never()).lockedAccounts();
        verify(unlockAccount, never()).unlock(any(), any());
    }

    @Test
    @DisplayName("いないアカウントの解除は 404")
    void reportsMissingAccount() throws Exception {
        when(unlockAccount.unlock(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/admin/accounts/nobody/unlock")
                        .header(AuthenticatedUser.USER_ID_HEADER, "admin01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isNotFound());
    }
}
