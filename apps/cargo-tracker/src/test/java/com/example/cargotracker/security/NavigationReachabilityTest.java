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

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 経路割り当て待ちの予約を 1 件用意する。空の一覧では導線を確かめられない。 */
    private void 引き渡し済みの予約を用意する() {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        java.util.UUID shipperId = java.util.UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '山田物産株式会社', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """,
                shipperId, "SHP-%06d".formatted(seq), "nav-%d@example.com".formatted(seq));
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status)
                VALUES (?, ?, 'GENERAL', 1000.000, 'JPOSA', 'USLAX',
                        CURRENT_DATE + 30, 'ROUTE_PROPOSED', 'NOT_ROUTED')
                """,
                java.util.UUID.randomUUID(), shipperId);
    }

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

    /**
     * 経路設計者が作業入口に到達できる（IT3）。
     *
     * <p><strong>IT1・IT2 では経路設計者に開く画面が 1 つも無かった。</strong>
     * 本 IT で「現在ご利用いただける機能はありません」の対象から外れる。
     */
    @Test
    @WithMockUser(username = "router", roles = "ROUTER")
    void 経路設計者はダッシュボードから経路設計と航路管理に到達できる() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/routing/queue\"")))
                .andExpect(content().string(Matchers.containsString("href=\"/voyages\"")))
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("ご利用いただける機能はありません"))));
    }

    @Test
    @WithMockUser(username = "router", roles = "ROUTER")
    void 航路一覧から新規登録に到達できる() throws Exception {
        mockMvc.perform(get("/voyages"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/voyages/new\"")));
    }

    /**
     * 経路設計者以外には航路管理の導線が出ない。
     *
     * <p>**「見せる」と「見せない」は別のテストで確かめる**（IT2 ふりかえり T1）。
     */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者には航路管理の導線が表示されない() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("href=\"/voyages\""))))
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("href=\"/routing/queue\""))));
    }

    /** 導線を消すだけでは足りない。URL を直接叩いても開けないことを確かめる。 */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者は航路管理をURL直打ちでも開けない() throws Exception {
        mockMvc.perform(get("/voyages")).andExpect(status().isForbidden());
        mockMvc.perform(get("/voyages/new")).andExpect(status().isForbidden());
        mockMvc.perform(get("/routing/queue")).andExpect(status().isForbidden());
    }

    /**
     * 経路設計者は予約の<strong>一覧</strong>は開けない。
     *
     * <p>経路設計者が見るのは「引き渡された予約」であり、全予約ではない。
     * 一覧は営業担当者の作業道具である。
     */
    @Test
    @WithMockUser(username = "router", roles = "ROUTER")
    void 経路設計者は貨物予約一覧をURL直打ちでも開けない() throws Exception {
        mockMvc.perform(get("/bookings")).andExpect(status().isForbidden());
        mockMvc.perform(get("/bookings/new")).andExpect(status().isForbidden());
    }

    /**
     * 経路設計者は予約<strong>詳細</strong>を開ける。
     *
     * <p><strong>行き止まりの画面を作らない。</strong> 経路割り当て待ち一覧から
     * 予約の内容を確認できないと、どの便を選べばよいか判断できない。
     */
    @Test
    @WithMockUser(username = "router", roles = "ROUTER")
    void 経路設計者は予約詳細を開ける() throws Exception {
        // 存在しない予約でも 403 ではなく 404 になる（認可は通っている）
        mockMvc.perform(get("/bookings/{id}", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
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

    /**
     * 経路割り当て待ち一覧から<strong>経路割り当てへ進める</strong>（US08）。
     *
     * <p>IT3 では一覧が行き止まりだった。開ける画面を増やすだけでは足りず、
     * <strong>そこから次に何ができるか</strong>まで確かめる。
     */
    @Test
    @WithMockUser(username = "router", roles = "ROUTER")
    void 経路割り当て待ちから経路割り当てへ進める() throws Exception {
        引き渡し済みの予約を用意する();

        mockMvc.perform(get("/routing/queue"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("経路を割り当て")));
    }
}
