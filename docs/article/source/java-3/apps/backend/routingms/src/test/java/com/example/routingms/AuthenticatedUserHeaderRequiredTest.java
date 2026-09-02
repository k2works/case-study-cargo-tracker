package com.example.routingms;

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
 * <p>IT3 までの routingms には、フィルタを<strong>登録したこと</strong>を確かめる検査しか
 * 無かった（しかもソースの字面を正規表現で見るものだった）。登録の字面があっても、
 * 除外パスを広げれば業務エンドポイントは開く。**安全装置は「入れたこと」ではなく
 * 「働くこと」で確かめる**（IT4 タスク 0.3）。
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
        mockMvc.perform(get("/api/v1/voyages")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ロールヘッダだけでは利用者が特定できないため拒否される")
    void rejectsRequestWithoutUserId() throws Exception {
        mockMvc.perform(get("/api/v1/voyages").header(AuthenticatedUser.ROLES_HEADER, "ROLE_ROUTING"))
                .andExpect(status().isUnauthorized());
    }

    /** 認可を書き忘れたエンドポイントでも 401 に倒れることを、実在の POST で確かめる。 */
    @Test
    @DisplayName("書き込み系も認可判定より前に 401 になる")
    void rejectsWriteRequestBeforeAuthorization() throws Exception {
        mockMvc.perform(post("/api/v1/voyages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * ヘルスチェックは必ず通す。
     *
     * <p>横断的な防御を一律に適用すると、liveness / readiness が 401 を受けて
     * 再起動ループに入る。健全かどうか（200/503）はここの関心ではない。
     */
    @Test
    @DisplayName("ヘルスチェックはヘッダ無しでも通る（一律適用は再起動ループを招く）")
    void allowsHealthProbeWithoutHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is(not(HttpStatus.UNAUTHORIZED.value())));
    }
}
