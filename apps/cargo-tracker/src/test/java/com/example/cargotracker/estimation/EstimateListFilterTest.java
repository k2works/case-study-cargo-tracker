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
import org.junit.jupiter.api.BeforeEach;
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
     * <strong>他のテストが作った見積を数えない。</strong> 一覧の件数を見る検査であり、
     * 残っていると「絞り込めた」のか「たまたま出なかった」のか区別できない。
     */
    @BeforeEach
    void 見積を空にする() {
        jdbcTemplate.update("DELETE FROM route_candidate");
        jdbcTemplate.update("DELETE FROM estimate");
    }

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
        return location.substring(location.lastIndexOf('/') + 1);
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
                .as("今日作った見積は、昨日からの範囲に入る")
                .contains(today);
        assertThat(一覧("?createdTo=" + yesterday))
                .as("今日作った見積は、昨日までの範囲に入らない")
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
}
