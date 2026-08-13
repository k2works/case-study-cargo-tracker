package com.example.cargotracker.tracking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 追跡照会の認可と到達性（US18。IT7 設計反映 #1・#2）。
 *
 * <p>着手前の突合で 2 つの欠落が見つかった。
 *
 * <ol>
 *   <li><strong>認可規則が設計と食い違っていた。</strong> {@code ui_design.md} は
 *       貨物追跡入力 {@code /tracking} と追跡詳細を
 *       {@code ROLE_SHIPPER, ROLE_CONSIGNEE, ROLE_TRACKER} と定めるのに、
 *       {@code SecurityConfig} は {@code /tracking/**} を ROLE_TRACKER 限定に
 *       していた（IT6 で発行待ち一覧を {@code /tracking/queue} に置いたときの規則）</li>
 *   <li><strong>開いてよいと定めた画面を開ける利用者が 1 人も存在しなかった。</strong>
 *       {@code Role} 列挙子に SHIPPER・CONSIGNEE はあるが、シードに利用者が無い</li>
 * </ol>
 *
 * <p><strong>規則の順序が要である。</strong> {@code /tracking/queue} を
 * {@code /tracking/**} より後ろに書くと効かず、<strong>発行待ち一覧が荷主に見える</strong>。
 * ここは「入口を回した」だけでは足りず、<strong>出口（403 になること）まで回す</strong>。
 */
@AutoConfigureMockMvc
@DisplayName("追跡照会の認可と到達性（US18）")
class TrackingAccessTest extends PostgreSQLIntegrationTestBase {

    // ---- 開ける側 ----

    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void 荷主は追跡入力画面を開ける() throws Exception {
        mockMvc.perform(get("/tracking")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "consignee", roles = "CONSIGNEE")
    void 荷受人は追跡入力画面を開ける() throws Exception {
        mockMvc.perform(get("/tracking")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "tracker", roles = "TRACKER")
    void 追跡管理者も追跡入力画面を開ける() throws Exception {
        mockMvc.perform(get("/tracking")).andExpect(status().isOk());
    }

    // ---- 開けない側（出口） ----

    /**
     * <strong>発行待ち一覧は荷主に見せない。</strong> そこには他社を含む
     * 確定済み予約が並ぶ。規則の順序を誤ると、ここが 200 で通る。
     */
    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void 荷主は追跡番号発行待ち一覧を開けない() throws Exception {
        mockMvc.perform(get("/tracking/queue")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "consignee", roles = "CONSIGNEE")
    void 荷受人も追跡番号発行待ち一覧を開けない() throws Exception {
        mockMvc.perform(get("/tracking/queue")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "tracker", roles = "TRACKER")
    void 追跡管理者は追跡番号発行待ち一覧を開ける() throws Exception {
        mockMvc.perform(get("/tracking/queue")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "handler", roles = "HANDLER")
    void 荷役作業員は追跡照会を開けない() throws Exception {
        mockMvc.perform(get("/tracking")).andExpect(status().isForbidden());
    }

    // ---- 導線（ロール別到達性） ----

    /**
     * <strong>開けると定めた画面には導線を用意する。</strong>
     * 機能を作っても導線が無ければ誰も使えない（{@code ui_design.md} の DoD）。
     */
    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void 荷主はダッシュボードから貨物追跡に到達できる() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/tracking")));
    }

    @Test
    @WithMockUser(username = "consignee", roles = "CONSIGNEE")
    void 荷受人はダッシュボードから貨物追跡に到達できる() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/tracking")));
    }

    /**
     * <strong>権限の無いロールにリンクを出さない。</strong> 出すと、
     * クリックして 403 に突き当たるという体験になる。
     */
    @Test
    @WithMockUser(username = "handler", roles = "HANDLER")
    void 荷役作業員には貨物追跡の導線が表示されない() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("貨物追跡"))));
    }

    /**
     * <strong>公開追跡はログイン画面から辿り着ける。</strong>
     *
     * <p>認証を持たない相手に開いた唯一の画面でありながら、
     * <strong>その相手が URL を知らなければどこからも到達できなかった</strong>。
     * 荷主から番号だけを伝えられた取引先は、ログイン画面まで来て行き止まりになる。
     *
     * <p>ロール別の到達性（navbar・ダッシュボード）は認証済みの利用者にしか働かない。
     * <strong>未認証の利用者にとっての「作業入口」はログイン画面である。</strong>
     */
    @Test
    void ログイン画面から公開追跡に到達できる() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("/public/tracking")));
    }

    /** 導線をクリックした先が実際に開ける（リンク切れを作らない）。 */
    @Test
    void ログインせずに公開追跡を開ける() throws Exception {
        mockMvc.perform(get("/public/tracking")).andExpect(status().isOk());
    }
}
