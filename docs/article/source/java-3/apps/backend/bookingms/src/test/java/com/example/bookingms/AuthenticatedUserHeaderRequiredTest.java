package com.example.bookingms;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-007 のコンプライアンス。Gateway が付けた利用者ヘッダの無いリクエストを 401 で拒否する。
 *
 * <p>この検査が守るのは「認可を書き忘れた 1 本が無認証で開く」ことである。ロール検査を書いた
 * 画面は守られるが、書き忘れたエンドポイントはヘッダが無くても素通りしてしまう。フィルタを
 * 外すとこのテストが赤になることを確認してから採用した。
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
        mockMvc.perform(get("/api/v1/shippers")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ロールヘッダだけでは利用者が特定できないため拒否される")
    void rejectsRequestWithoutUserId() throws Exception {
        mockMvc.perform(get("/api/v1/shippers").header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 認可を書き忘れたエンドポイントでも 401 に倒れることを、実在の POST で確かめる。
     *
     * <p>ヘッダの検査を各エンドポイントの引数（{@code @RequestHeader}）に任せると、
     * 書き忘れた 1 本だけが無認証で開く。横断的な検査がその穴を塞ぐ。
     */
    @Test
    @DisplayName("書き込み系も認可判定より前に 401 になる")
    void rejectsWriteRequestBeforeAuthorization() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ヘルスチェックはヘッダ無しでも通る（一律適用は再起動ループを招く）")
    void allowsHealthProbeWithoutHeaders() throws Exception {
        // 健全かどうか（200/503）はこの検査の関心ではない。フィルタが弾いていないこと（401 でないこと）を見る
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is(not(HttpStatus.UNAUTHORIZED.value())));
    }
}
