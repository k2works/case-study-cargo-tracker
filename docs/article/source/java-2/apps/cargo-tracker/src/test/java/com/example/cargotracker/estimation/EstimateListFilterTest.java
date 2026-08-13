package com.example.cargotracker.estimation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 見積一覧の絞り込み（{@code ui_design.md}「見積一覧」。IT19 の C4）。
 *
 * <p><strong>一覧は「毎朝どう使うか」から確かめる。</strong> 期限切れが混ざったままだと、
 * どれがまだ使えるのか分からず<strong>一覧全体が信用されない</strong>。
 *
 * <p><strong>状態は保存された列で絞らない。</strong> {@code estimate.status} は
 * 作成時に書き込まれたまま更新されない列であり、読み取りは希望期限と業務日から
 * 導出している（ADR-019 と同じ考え方）。列で絞ると<strong>いつでも 0 件</strong>になる。
 */
@DisplayName("見積一覧の絞り込み（C4）")
@AutoConfigureMockMvc
class EstimateListFilterTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private java.time.Clock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * このクラスがcreatedEstimatesだけを片付ける。
     *
     * <p><strong>全消しにしない</strong>（クローズ前レビュー）。実 DB はテストクラス間で
     * 共有しており、{@code DELETE FROM estimate} は<strong>他のテストが必要とする行まで
     * 消す</strong>。しかも動作確認用データは印で再投入を塞いでいるため、
     * <strong>消された側は復旧できない</strong>。
     *
     * <p>各テストは「自分がcreatedEstimates ID が出るか」を見ており、
     * <strong>全体の件数には依存していない</strong>。
     */
    @AfterEach
    void 作った見積だけを片付ける() {
        for (String estimateId : createdEstimates) {
            jdbcTemplate.update(
                    "DELETE FROM route_candidate WHERE estimate_id ="
                            + " (SELECT id FROM estimate WHERE CAST(estimate_id AS VARCHAR) = ?)",
                    estimateId);
            jdbcTemplate.update(
                    "DELETE FROM estimate WHERE CAST(estimate_id AS VARCHAR) = ?", estimateId);
        }
        createdEstimates.clear();
    }

    /** このクラスがcreatedEstimatesの ID。 */
    private final java.util.List<String> createdEstimates = new java.util.ArrayList<>();

    private String 見積を作る(String origin, String destination, int deadlineInDays)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/estimates")
                        .param("origin", origin)
                        .param("destination", destination)
                        .param("arrivalDeadline",
                                LocalDate.now(clock).plusDays(deadlineInDays).toString())
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1500")
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        String estimateId = location.substring(location.lastIndexOf('/') + 1);
        createdEstimates.add(estimateId);
        return estimateId;
    }

    private void 期限を過ぎさせる(String estimateId) {
        jdbcTemplate.update("""
                UPDATE estimate SET arrival_deadline = CURRENT_DATE - 1
                 WHERE CAST(estimate_id AS VARCHAR) = ?
                """, estimateId);
    }

    private String 一覧(String query) throws Exception {
        return mockMvc.perform(get("/estimates" + query).with(user("sales1").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 出発地で絞れる() throws Exception {
        String osaka = 見積を作る("JPOSA", "USLAX", 30);
        String yokohama = 見積を作る("JPYOK", "DEHAM", 30);

        String html = 一覧("?origin=JPOSA");

        assertThat(html).contains(osaka);
        assertThat(html).doesNotContain(yokohama);
    }

    @Test
    void 目的地で絞れる() throws Exception {
        String toLosAngeles = 見積を作る("JPOSA", "USLAX", 30);
        String toHamburg = 見積を作る("JPYOK", "DEHAM", 30);

        String html = 一覧("?destination=DEHAM");

        assertThat(html).contains(toHamburg);
        assertThat(html).doesNotContain(toLosAngeles);
    }

    @Test
    void 作成日の範囲で絞れる() throws Exception {
        String today = 見積を作る("JPOSA", "USLAX", 30);
        String yesterday = LocalDate.now(clock).minusDays(1).toString();

        assertThat(一覧("?createdFrom=" + yesterday))
                .as("今日createdEstimatesは、昨日からの範囲に入る")
                .contains(today);
        assertThat(一覧("?createdTo=" + yesterday))
                .as("今日createdEstimatesは、昨日までの範囲に入らない")
                .doesNotContain(today);
    }

    /**
     * <strong>状態で絞ると、期限切れだけが出る。</strong>
     *
     * <p>保存された列で絞ると<strong>いつでも 0 件</strong>になる。
     * この検査は「絞れること」と「0 件にならないこと」を同時に見る。
     */
    @Test
    void 状態で絞ると期限切れだけが出る() throws Exception {
        String valid = 見積を作る("JPOSA", "USLAX", 30);
        String expired = 見積を作る("JPYOK", "DEHAM", 1);
        期限を過ぎさせる(expired);

        String html = 一覧("?status=EXPIRED");

        assertThat(html)
                .as("**0 件にならない**（保存された列で絞っていない）")
                .contains(expired);
        assertThat(html).doesNotContain(valid);
    }

    @Test
    void 状態で絞ると有効なものだけが出る() throws Exception {
        String valid = 見積を作る("JPOSA", "USLAX", 30);
        String expired = 見積を作る("JPYOK", "DEHAM", 1);
        期限を過ぎさせる(expired);

        String html = 一覧("?status=CREATED");

        assertThat(html).contains(valid);
        assertThat(html).doesNotContain(expired);
    }

    /**
     * <strong>絞り込んだ結果が 0 件でも、次の行動へ繋ぐ。</strong>
     *
     * <p>「見積がまだありません」は<strong>初めて開いた人向けの文言</strong>であり、
     * 絞り込んで 0 件だった人に「新規作成へ」と促すのは筋が違う ——
     * <strong>その人が変えたいのは条件である</strong>。
     */
    @Test
    void 絞り込んで零件のときは条件を見直させる() throws Exception {
        見積を作る("JPOSA", "USLAX", 30);

        String html = 一覧("?origin=NLRTM");

        assertThat(html).contains("条件に一致する見積はありません");
        assertThat(html)
                .as("**初めて開いた人向けの文言を出さない**")
                .doesNotContain("見積がまだありません");
    }

    /**
     * <strong>何も指定せずに開くと、有効な見積だけが出る</strong>（U4。IT20）。
     *
     * <p>期限切れが混ざったままだと、毎朝の確認でどれがまだ使えるのか分からず
     * <strong>一覧全体が信用されない</strong>。
     */
    @Test
    void 既定では期限切れを混ぜない() throws Exception {
        String valid = 見積を作る("JPOSA", "USLAX", 30);
        String expired = 見積を作る("JPYOK", "DEHAM", 1);
        期限を過ぎさせる(expired);

        String html = 一覧("");

        assertThat(html).contains(valid);
        assertThat(html)
                .as("**既定で期限切れを出さない**（U4）")
                .doesNotContain(expired);
    }

    /**
     * <strong>既定で隠したものを取り戻せる</strong>（U4。IT20）。
     *
     * <p>取り戻す手段が無い既定は、<strong>見えなくしただけ</strong>である。
     */
    @Test
    void すべてを選べば期限切れも出る() throws Exception {
        String valid = 見積を作る("JPOSA", "USLAX", 30);
        String expired = 見積を作る("JPYOK", "DEHAM", 1);
        期限を過ぎさせる(expired);

        String html = 一覧("?status=ALL");

        assertThat(html).contains(valid);
        assertThat(html).contains(expired);
    }

    /**
     * <strong>港コードの打ち間違いを、条件の不一致と混同しない</strong>（U5 の (c)。IT20）。
     *
     * <p>これを分けないと、打ち間違えた人は<strong>条件を何度変えても 0 件のまま</strong>
     * になる。<strong>気づく手段は次の行動へ繋がっていなければ仕事が進まない。</strong>
     */
    @Test
    void 実在しない港コードはそう伝える() throws Exception {
        見積を作る("JPOSA", "USLAX", 30);

        String html = 一覧("?origin=ZZZZZ");

        assertThat(html).contains("港マスタにありません");
        assertThat(html)
                .as("**条件の不一致と混同しない**")
                .doesNotContain("条件に一致する見積はありません");
    }

    /**
     * <strong>件数と並び順を出す</strong>（U6。IT20）。
     *
     * <p>月末に「今月何件出したか」を聞かれる。並び順は<strong>書いていないと
     * 読む人が毎回推測する</strong>。
     *
     * <p><strong>描画された形でアサートする</strong>（IT19 の Try T2）。
     */
    @Test
    void 件数と並び順を出す() throws Exception {
        // **実 DB は他のテストと共有している。** 絶対値でアサートすると、
        // 原因でないテストが増えただけで落ちる（IT19 の教訓と同じ形）
        int before = 該当件数(一覧("?origin=JPOSA&status=ALL"));

        見積を作る("JPOSA", "USLAX", 30);
        String html = 一覧("?origin=JPOSA&status=ALL");

        assertThat(該当件数(html))
                .as("**件数は絞り込んだ結果を数えている**")
                .isEqualTo(before + 1);
        assertThat(html)
                .as("**並び順を書いていないと、読む人が毎回推測する**")
                .contains("作成日の新しい順");
        assertThat(html)
                .as("**何で絞った結果かを書く**（レビュー H7。月末の数え落としを防ぐ）")
                .contains("すべての状態");
    }

    /** 画面に描かれた「該当 N 件」の N。<strong>描画された形で読む</strong>（Try T2）。 */
    private static int 該当件数(String html) {
        var matcher = java.util.regex.Pattern
                .compile("該当\\s*<span[^>]*>(\\d+)</span>\\s*件")
                .matcher(html);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        // **0 件のときは件数行そのものが出ない。** ただし「行はあるのに件数が無い」を
        // 黙って 0 と読むと、テンプレートが壊れても緑になる（レビュー L3）
        if (!html.contains("alert")) {
            throw new AssertionError("0 件でもないのに「該当 N 件」が出ていません（レビュー L3）");
        }
        return 0;
    }

    /**
     * <strong>期限切れの行から直接作り直せる</strong>（U8。IT20）。
     *
     * <p>いままでは 1 件ずつ詳細を開いて戻っており、10 件なら 20 回の遷移になる。
     *
     * <p><strong>有効な見積には「同じ条件で作成」を出す</strong>（レビュー M8）。
     * 実務で最も多いのは「同じ荷主・同じ区間で重量違いをもう 1 本」であり、
     * <strong>期限切れだけに出すと、いちばん多い操作に導線が無い</strong>。
     */
    @Test
    void 期限切れは再見積で有効は作成として出す() throws Exception {
        String valid = 見積を作る("JPOSA", "USLAX", 30);
        String expired = 見積を作る("JPYOK", "DEHAM", 1);
        期限を過ぎさせる(expired);

        // **他クラスが残した見積に影響されないよう、出発地で絞る**（レビュー M12）
        assertThat(一覧("?origin=JPYOK&status=ALL"))
                .as("**条件を引き継いだ作成フォームへ送る**")
                .contains("/estimates/new?origin=JPYOK&amp;destination=DEHAM");
        assertThat(一覧("?origin=JPOSA&status=ALL"))
                .as("**有効な見積は「再見積」ではなく「作成」である**")
                .contains(valid)
                .contains("同じ条件で作成");
    }

    /**
     * <strong>並び順は「書いてあること」ではなく「そうなっていること」を確かめる</strong>
     * （クローズ前レビュー M1）。
     *
     * <p>文言の有無しか見ていないと、{@code ORDER BY} を {@code ASC} に変えても
     * 消しても<strong>緑のまま</strong>になる。
     */
    @Test
    void 作成日の新しい順に並ぶ() throws Exception {
        String older = 見積を作る("JPOSA", "USLAX", 30);
        String newer = 見積を作る("JPOSA", "USLAX", 31);
        jdbcTemplate.update(
                "UPDATE estimate SET created_at = created_at - INTERVAL '2 days'"
                        + " WHERE CAST(estimate_id AS VARCHAR) = ?",
                older);

        String html = 一覧("?origin=JPOSA&status=ALL");

        assertThat(html.indexOf(newer))
                .as("**新しいほうが先に出る**")
                .isLessThan(html.indexOf(older));
    }

    /**
     * <strong>条件を入れた 0 件は、既定が隠しただけの 0 件と区別する</strong>
     * （クローズ前レビュー H6）。
     *
     * <p>既定が隠しただけの側（{@code HIDDEN_BY_DEFAULT}）は
     * {@code EstimateFilterTest} が規則として確かめている ——
     * 実 DB は他のテストと共有しており、<strong>有効な見積が 1 件も無い状況は作れない</strong>。
     */
    @Test
    void 条件を入れた零件は条件の不一致として伝える() throws Exception {
        見積を作る("JPOSA", "USLAX", 30);

        String html = 一覧("?origin=NLRTM&status=ALL");

        assertThat(html).contains("条件に一致する見積はありません");
        assertThat(html)
                .as("**既定が隠しただけ、とは言わない**")
                .doesNotContain("期限切れは既定で表示しません");
    }

    /** <strong>実在しない港コードを名指しする</strong>（クローズ前レビュー M6）。 */
    @Test
    void 実在しない港コードを名指しする() throws Exception {
        見積を作る("JPOSA", "USLAX", 30);

        String html = 一覧("?origin=ZZZZZ");

        assertThat(html).contains("ZZZZZ");
        assertThat(html).contains("港マスタにありません");
    }

    /** <strong>作成日の範囲が逆なら、何を入れても 0 件になる</strong>（U5 の (d)。IT20）。 */
    @Test
    void 作成日の範囲が逆ならそう伝える() throws Exception {
        見積を作る("JPOSA", "USLAX", 30);
        String today = LocalDate.now(clock).toString();
        String yesterday = LocalDate.now(clock).minusDays(1).toString();

        String html = 一覧("?createdFrom=" + today + "&createdTo=" + yesterday);

        assertThat(html).contains("「から」が「まで」より後になっています");
        assertThat(html).doesNotContain("条件に一致する見積はありません");
    }
}
