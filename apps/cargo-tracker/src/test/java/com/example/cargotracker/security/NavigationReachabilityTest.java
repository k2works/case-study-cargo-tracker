package com.example.cargotracker.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * ロール別の「作業入口」への到達性を検証する（{@code ui_design.md} の DoD）。
 *
 * <p><strong>画面が受け入れ基準を満たしていても、そのロールが navbar やダッシュボードから
 * 到達できなければ業務は成立しない。</strong> 認可（403 を返すこと）は
 * {@link AuthenticationTest} が担保するが、認可が正しくても導線が無ければ
 * 担当者はその画面に辿り着けない。両者は別の欠陥であり、別に検証する。
 *
 * <p>逆方向も検証する。権限の無いロールにリンクが見えていると、
 * クリックして 403 に突き当たるという体験になる。出さないことまでが設計である。
 */
@AutoConfigureMockMvc
class NavigationReachabilityTest extends PostgreSQLIntegrationTestBase {

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者はダッシュボードから荷主管理に到達できる() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                // navbar のリンク
                .andExpect(content().string(Matchers.containsString("荷主管理")))
                // 作業カードの入口
                .andExpect(content().string(Matchers.containsString("href=\"/shippers\"")));
    }

    @Test
    @WithMockUser(username = "handler", roles = "HANDLER")
    void 権限のないロールには荷主管理の導線が表示されない() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("href=\"/shippers\""))));
    }

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 荷主一覧から新規登録に到達できる() throws Exception {
        mockMvc.perform(get("/shippers"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/shippers/new\"")));
    }

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 全画面からダッシュボードに戻れる() throws Exception {
        // 行き止まりの画面を作らない（ui_design.md）
        for (String path : new String[] {"/shippers", "/shippers/new"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("href=\"/\"")));
        }
    }
}
