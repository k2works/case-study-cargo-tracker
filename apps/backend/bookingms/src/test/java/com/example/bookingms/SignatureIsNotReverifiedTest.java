package com.example.bookingms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shared.auth.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-004 のコンプライアンス (b)。bookingms が JWT の署名を再検証しないことを固定する。
 *
 * <p>本 ADR が最も恐れる失敗モードは「サービス側に Spring Security を素直に入れたら
 * 署名検証まで付いてきて、鍵の管理が 7 サービスに拡散する」ことである。この検査だけが
 * それを止める。署名が壊れた（あるいは存在しない）トークンでも、Gateway が付けた
 * ロールのクレームさえ正しければ処理されることを確かめる。
 *
 * <p>これは「誰でも入れる」ことを許しているのではない。Gateway を通らない経路が存在しないことは
 * ネットワーク層で担保し、このサービスの関心はロールに基づく認可だけに絞るという分担である。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(SpringExtension.class)
@ActiveProfiles("integration")
@DisplayName("署名の再検証を行わない（ADR-004）")
class SignatureIsNotReverifiedTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Authorization ヘッダが無くても、検証済みクレームがあれば処理する")
    void doesNotRequireBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/shippers")
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("壊れた署名のトークンが付いていても、署名を理由に拒否しない")
    void ignoresBrokenSignature() throws Exception {
        // 署名が明らかに不正な JWT。ここで 401 になるなら、このサービスが署名を見ている証拠になる
        String forged = "eyJhbGciOiJIUzI1NiJ9."
                + "eyJzdWIiOiJhdHRhY2tlciIsInJvbGVzIjpbIlJPTEVfQURNSU4iXX0."
                + "definitely-not-a-valid-signature";

        mockMvc.perform(get("/api/v1/shippers")
                        .header("Authorization", "Bearer " + forged)
                        .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("トークンの中身ではなく Gateway が付けたクレームで認可する")
    void authorizesByForwardedClaimsOnly() throws Exception {
        // トークンには ROLE_ADMIN と書いてあるが、Gateway が付けたロールは ROLE_HANDLER。
        // 後者で判断するため 403 になる
        String forged = "eyJhbGciOiJIUzI1NiJ9."
                + "eyJzdWIiOiJhdHRhY2tlciIsInJvbGVzIjpbIlJPTEVfU0FMRVMiXX0."
                + "definitely-not-a-valid-signature";

        mockMvc.perform(get("/api/v1/shippers")
                        .header("Authorization", "Bearer " + forged)
                        .header(AuthenticatedUser.USER_ID_HEADER, "handler01")
                        .header(AuthenticatedUser.ROLES_HEADER, "ROLE_HANDLER"))
                .andExpect(status().isForbidden());
    }
}
