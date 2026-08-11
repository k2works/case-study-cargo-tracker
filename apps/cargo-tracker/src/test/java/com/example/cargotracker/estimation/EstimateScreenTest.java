package com.example.cargotracker.estimation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 見積の画面と到達性（US01）。
 *
 * <p><strong>営業担当者が最初に触る画面である。</strong> 見積は予約の前段であり、
 * 荷主に予算と納期を伝えるための唯一の手段である（`user_story.md` の US01）。
 *
 * <p><strong>到達性は「行けるか」と「押せるか」の両方を見る</strong>（IT17 の Try T1）。
 * IT17 で、認可を広げたのに画面の出し分けを見直さず、
 * <strong>押すと 403 になるボタンを出していた</strong>。その裏返しをここで防ぐ。
 */
@DisplayName("見積の画面と到達性（US01）")
@AutoConfigureMockMvc
class EstimateScreenTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** <strong>営業担当者が見積一覧を開ける。</strong> */
    @Test
    void 営業担当者は見積一覧を開ける() throws Exception {
        mockMvc.perform(get("/estimates").with(user("sales1").roles("SALES")))
                .andExpect(status().isOk());
    }

    /**
     * <strong>営業担当者以外は見積を開けない。</strong>
     *
     * <p>すべてのロールに開く実装でも上のテストは緑になる —
     * <strong>開くことと開かないことの両方を見る。</strong>
     */
    @Test
    void 営業担当者以外は見積を開けない() throws Exception {
        mockMvc.perform(get("/estimates").with(user("handler1").roles("HANDLER")))
                .andExpect(status().isForbidden());
    }

    /**
     * <strong>ダッシュボードと画面上部の両方から見積へ行ける。</strong>
     *
     * <p><strong>画面を作っても、そこへ行けなければ仕事は始まらない。</strong>
     * ナビゲーション構成表（`ui_design.md`）は「見積管理」を ROLE_SALES に出すと
     * 定めている。
     *
     * <p><strong>「見積管理」を数えるだけでは、2 つの入口を判別しない。</strong>
     * ダッシュボードは画面上部のナビを含むため、<strong>カードを消しても
     * ナビの文字列で緑になる</strong>（破壊検証で実際に踏んだ）。
     * カードにしか無い説明文で、それぞれを別に見る。
     */
    @Test
    void 営業担当者はダッシュボードと画面上部の両方から見積へ行ける() throws Exception {
        String dashboard = mockMvc.perform(get("/").with(user("sales1").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(dashboard)
                .as("**ダッシュボードのカード**（カードにしか無い説明文で見る）")
                .contains("輸送条件から概算を作り");
        assertThat(navbarOf(dashboard))
                .as("**画面上部のナビ**")
                .contains("見積管理")
                .contains("/estimates");
    }

    /**
     * 画面上部のナビだけを取り出す。
     *
     * <p><strong>画面全体で判定しない。</strong> ダッシュボードはナビを含むため、
     * 全体に「見積管理」があることは<strong>カードがあることを意味しない</strong>。
     */
    private static String navbarOf(String html) {
        int start = html.indexOf("<nav");
        int end = html.indexOf("</nav>", start);
        assertThat(start).as("ナビが見つからないなら、この検査は何も見ていない").isNotNegative();
        return html.substring(start, end);
    }

    /**
     * <strong>営業担当者以外のダッシュボードに見積の入口を出さない。</strong>
     *
     * <p>開けない画面への入口は、押した人を 403 へ連れて行く（IT17 の H1）。
     */
    @Test
    void 営業担当者以外に見積の入口を出さない() throws Exception {
        String dashboard = mockMvc.perform(get("/").with(user("handler1").roles("HANDLER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(dashboard).doesNotContain("/estimates");
    }

    /**
     * <strong>見積が 0 件のとき、次に何をすればよいかを出す。</strong>
     *
     * <p><strong>空の表だけを見せない</strong>（`ui_design.md` の空状態）。
     * 最初に開く人は必ず 0 件である。
     *
     * <p><strong>0 件の状態を自分で作る。</strong> 同じ DB を他のテストと共有しており、
     * <strong>実行順で結果が変わる</strong>（IT17 で 2 度踏んだ形）。
     * 「たまたま空だったから緑」にしない。
     */
    @Test
    void 見積が無いときは作成へ導く() throws Exception {
        jdbcTemplate.update("DELETE FROM route_candidate");
        jdbcTemplate.update("DELETE FROM estimate");

        String html = mockMvc.perform(get("/estimates").with(user("sales1").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("見積がまだありません");
        assertThat(html).contains("/estimates/new");
    }
}
