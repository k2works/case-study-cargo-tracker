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
    void 営業担当者はダッシュボードから貨物予約に到達できる() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("貨物予約")))
                .andExpect(content().string(Matchers.containsString("href=\"/bookings\"")));
    }

    /**
     * 荷主には貨物予約の導線を出さない。
     *
     * <p><strong>利用者アカウントと荷主を結びつける手段がまだ無い。</strong>
     * この状態で一覧を開放すると、荷主から**他社の予約まで見える**。
     * {@code non_functional.md} は ROLE_SHIPPER を「自社予約・追跡（Phase 2）」と
     * 定めており、「自社の」を実現できない今、開放は正典に反する。
     *
     * <p>IT2 の実装で一度開放してしまい、レビューで気づいて戻した。
     * **ロール別の到達性は「見せること」だけでなく「見せないこと」も含む。**
     */
    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void 荷主には貨物予約の導線が表示されない() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("href=\"/bookings\""))));
    }

    /** 導線を消すだけでは足りない。URL を直接叩いても開けないことを確かめる。 */
    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void 荷主は貨物予約一覧をURL直打ちでも開けない() throws Exception {
        mockMvc.perform(get("/bookings")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "handler", roles = "HANDLER")
    void 権限のないロールには貨物予約の導線が表示されない() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("href=\"/bookings\""))));
    }

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 貨物予約一覧から新規登録に到達できる() throws Exception {
        mockMvc.perform(get("/bookings"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/bookings/new\"")));
    }

    /** 荷主詳細から予約登録へ直接進める（IT1 のユーザー代表レビュー）。 */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 荷主詳細からその荷主で予約するに到達できる() throws Exception {
        mockMvc.perform(get("/shippers"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 全画面からダッシュボードに戻れる() throws Exception {
        // 行き止まりの画面を作らない（ui_design.md）
        for (String path : new String[] {"/shippers", "/shippers/new", "/bookings", "/bookings/new"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("href=\"/\"")));
        }
    }
}
