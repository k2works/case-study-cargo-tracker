package com.example.cargotracker.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;

/**
 * 公開エンドポイントのレートリミット（{@code non_functional.md} §2.2 / §3.4）。
 *
 * <p>公開追跡は<strong>認証を持たない相手に開いた唯一の入口</strong>であり、
 * 追跡番号は日付＋連番という<strong>推測できる形</strong>をしている。
 * 総当たりを止める手立てが無いと、貨物の有無を端から確かめられる。
 *
 * <p><strong>そして本テストの主眼は、防御そのものより除外の側にある。</strong>
 * 過負荷時にヘルスチェックまで制限されると、liveness が 503 を返して ECS が
 * タスクを再起動し、<strong>残ったタスクの負荷がさらに上がって次も 503 になる</strong> —
 * 負荷を減らすための防御が、負荷を増やす再起動ループを引き起こす。
 *
 * <p>他の実装（Flix take-1 IT7）でこの故障モードを実測している。
 * <strong>「除外を書いた」ことと「除外が効いている」ことは別である。</strong>
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    // テストで到達できる上限にする。**コメントで「テストでは小さく」と書くだけでは
    // 小さくならない**（IT6 のポーリング間隔と同じ扱い）
    "cargotracker.public-rate-limit.enabled=true",
    "cargotracker.public-rate-limit.requests-per-window=3",
    "cargotracker.public-rate-limit.window=1m"
})
@DisplayName("公開エンドポイントのレートリミット")
class PublicRateLimitTest extends PostgreSQLIntegrationTestBase {

    private static final String CLIENT = "203.0.113.10";

    @Test
    void 上限を超えると429を返す() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/public/tracking").with(remote(CLIENT)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/public/tracking").with(remote(CLIENT)))
                .andExpect(status().isTooManyRequests())
                // 受入基準（ui_design.md）: 再試行までの目安秒数を示す
                .andExpect(content().string(Matchers.containsString("アクセスが集中")));
    }

    /**
     * <strong>再試行の目安を機械可読な形でも返す。</strong> 画面の文言だけでは、
     * QR から来たブラウザ以外（監視・スクリプト）が待つべき時間を知れない。
     */
    @Test
    void 制限時はRetryAfterを返す() throws Exception {
        String client = "203.0.113.11";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/public/tracking").with(remote(client)));
        }

        var response = mockMvc.perform(get("/public/tracking").with(remote(client)))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse();

        assertThat(response.getHeader("Retry-After")).isNotBlank();
    }

    /**
     * <strong>制限は送信元ごとに数える。</strong> 全体で 1 つの数え方にすると、
     * 誰か 1 人の総当たりで<strong>全員が締め出される</strong>。
     */
    @Test
    void 別の送信元は影響を受けない() throws Exception {
        String noisy = "203.0.113.20";
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/public/tracking").with(remote(noisy)));
        }

        mockMvc.perform(get("/public/tracking").with(remote("203.0.113.21")))
                .andExpect(status().isOk());
    }

    // ---- 除外（ここが本題） ----

    /**
     * <strong>ヘルスチェックは制限しない。</strong> 制限すると、過負荷時に
     * liveness が落ちて再起動ループに入る。<strong>防御が障害を作る。</strong>
     */
    @Test
    void ヘルスチェックは制限されない() throws Exception {
        String client = "203.0.113.30";
        // 上限の何倍叩いても通る
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/actuator/health").with(remote(client)))
                    .andExpect(status().isOk());
        }
    }

    /** liveness / readiness の個別プローブも同じく対象外である。 */
    @Test
    void 個別のプローブも制限されない() throws Exception {
        String client = "203.0.113.31";
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/actuator/health/liveness").with(remote(client)))
                    .andExpect(status().isOk());
        }
    }

    /**
     * <strong>制限するのは公開エンドポイントだけである。</strong> 業務画面は
     * 認証で守られており、ここまで制限すると<strong>繁忙期に現場が締め出される</strong>。
     */
    @Test
    void 業務画面は制限されない() throws Exception {
        String client = "203.0.113.40";
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/login").with(remote(client)))
                    .andExpect(status().isOk());
        }
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor
            remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
