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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 見積のルート候補は Routing の探索から来る（ADR-023。US01 の受入基準 2・3・5）。
 *
 * <p><strong>固定値のスタブでは、画面は動くのに数字が現実と無関係になる。</strong>
 * 見積は荷主に渡る数字であり、実在しない便の所要日数と費用を「概算」として
 * 提示することになる。
 *
 * <p><strong>この検査は固定値の実装なら落ちる。</strong> 登録した航海の番号が
 * 候補に出ることを見る。
 */
@DisplayName("見積のルート候補は実在する便から来る（ADR-023）")
@AutoConfigureMockMvc
class EstimateRouteCandidateTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    /** **テストも同じ時計で「今日」を決める。** 実時計だと業務のタイムゾーンとずれる */
    @Autowired
    private java.time.Clock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 出発地から目的地へ直行する便を 1 本用意する。 */
    private void 便を用意する(String voyageNumber, String origin, String destination, int days) {
        jdbcTemplate.update("""
                INSERT INTO voyage (
                    voyage_number, vessel_name, carrier_name, cargo_types,
                    capacity_weight_kg, version)
                VALUES (?, 'テスト丸', 'テスト海運', 'GENERAL,HAZARDOUS,REFRIGERATED', 50000, 0)
                """, voyageNumber);
        jdbcTemplate.update("""
                INSERT INTO carrier_movement (
                    voyage_id, departure_location_unlocode, arrival_location_unlocode,
                    departure_date, arrival_date, seq_number)
                SELECT id, ?, ?,
                       CURRENT_TIMESTAMP + INTERVAL '3 day',
                       CURRENT_TIMESTAMP + (INTERVAL '1 day' * ?), 0
                  FROM voyage WHERE voyage_number = ?
                """, origin, destination, 3 + days, voyageNumber);
    }

    private String 見積を作る(String origin, String destination, int deadlineInDays)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/estimates")
                        .param("origin", origin)
                        .param("destination", destination)
                        .param("arrivalDeadline", LocalDate.now(clock).plusDays(deadlineInDays).toString())
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1000")
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return result.getResponse().getHeader("Location");
    }

    private String 詳細(String location) throws Exception {
        return mockMvc.perform(get(location).with(user("sales1").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * <strong>登録した便の航海番号が候補に出る</strong>（受入基準 2・3）。
     *
     * <p><strong>固定値の実装ならここで落ちる。</strong>
     */
    @Test
    void 実在する便の航海番号が候補に出る() throws Exception {
        便を用意する("VEST0001", "JPOSA", "USLAX", 18);

        String html = 詳細(見積を作る("JPOSA", "USLAX", 60));

        assertThat(html)
                .as("**実在する便の番号が出ること**（固定値ではない）")
                .contains("VEST0001");
    }

    /**
     * <strong>その区間を走る便が無ければ、その旨を出す</strong>（受入基準 5）。
     *
     * <p><strong>「便が無い」と「期限に間に合わない」は別の事態である</strong>（ADR-023）。
     * 営業担当者が次に取る行動が違う —— 前者は別の港や他社、後者は荷主への
     * 期限延長の相談である。
     */
    @Test
    void 便が無ければその旨を出す() throws Exception {
        String html = 詳細(見積を作る("FRLEH", "NLRTM", 60));

        assertThat(html)
                .as("**便が無いことを、期限の問題と混ぜない。** 次の行動で見分ける")
                .contains("別の港")
                .doesNotContain("期限の延長");
    }

    /**
     * <strong>便はあるが期限に間に合わないときは、そう出す</strong>（受入基準 5）。
     *
     * <p>まとめて「候補がありません」と出すと、どちらなのか分からない。
     */
    @Test
    void 期限に間に合わなければその旨を出す() throws Exception {
        便を用意する("VEST0002", "KRPUS", "USSEA", 30);

        // **期限を便の所要日数より短くする**（便はあるが間に合わない）
        String html = 詳細(見積を作る("KRPUS", "USSEA", 5));

        assertThat(html)
                .as("**期限の問題であることが読めること。** 次の行動で見分ける")
                .contains("期限の延長")
                .doesNotContain("別の港");
    }

    /**
     * <strong>候補は保存される</strong>（受入基準 3・4）。
     *
     * <p>あとで作り直さない —— <strong>先週の見積と今日の見積で数字が違っては、
     * 荷主に伝えた内容を説明できない。</strong>
     */
    @Test
    void 候補は見積と一緒に保存される() throws Exception {
        便を用意する("VEST0003", "JPTYO", "SGSIN", 12);

        String location = 見積を作る("JPTYO", "SGSIN", 60);
        String estimateId = location.substring(location.lastIndexOf('/') + 1);

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM route_candidate c
                  JOIN estimate e ON e.id = c.estimate_id
                 WHERE CAST(e.estimate_id AS VARCHAR) = ?
                """, Integer.class, estimateId);
        assertThat(count).as("**候補が保存されていること**").isPositive();
    }
}
