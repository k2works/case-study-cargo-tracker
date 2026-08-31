package com.example.simulationms;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.auth.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-007 のコンプライアンス。Gateway が付けた利用者ヘッダの無いリクエストを 401 で拒否する。
 *
 * <p><strong>simulationms は業務データを作る。</strong>無認証で実行できると、誰でも
 * 予約と請求書を作れることになる——[ADR-030] 決定 4 が本番での起動を止めていても、
 * 検証環境で誰の操作か分からないデータが増える。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(SpringExtension.class)
@ActiveProfiles("integration")
@DisplayName("利用者ヘッダを必須とする（ADR-007）")
class AuthenticatedUserHeaderRequiredTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("利用者ヘッダが無い業務リクエストは 401 で拒否される")
    void rejectsRequestWithoutHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/simulations/scenarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ロールヘッダだけでは利用者が特定できないため拒否される")
    void rejectsRequestWithoutUserId() throws Exception {
        mockMvc.perform(get("/api/v1/simulations/scenarios")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ヘルスチェックはヘッダ無しでも通る（一律適用は再起動ループを招く）")
    void allowsHealthProbeWithoutHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is(not(HttpStatus.UNAUTHORIZED.value())));
    }
}
