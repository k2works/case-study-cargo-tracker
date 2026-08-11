package com.example.cargotracker.estimation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * 見積の作成（US01 の受入基準 1・4・6）。
 *
 * <p><strong>営業担当者が輸送条件を入れて概算を得る</strong>のが本ストーリーの中心である。
 */
@DisplayName("US01 見積の作成")
@AutoConfigureMockMvc
class EstimateCreationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String 期限(int plusDays) {
        return LocalDate.now().plusDays(plusDays).toString();
    }

    /** <strong>受入基準 1: 出発地・目的地・希望期限・貨物種別・重量を入力できる。</strong> */
    @Test
    void 見積の作成フォームを開ける() throws Exception {
        String html = mockMvc.perform(get("/estimates/new").with(user("sales1").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("origin").contains("destination")
                .contains("arrivalDeadline").contains("cargoType").contains("weightKg");
    }

    /**
     * <strong>受入基準 4: 見積情報が保存され、見積番号が発行される。</strong>
     *
     * <p>作成後は PRG で詳細へ送る（`ui_design.md`）。
     */
    @Test
    void 見積を作成すると番号が発行され詳細へ送られる() throws Exception {
        MvcResult result = mockMvc.perform(post("/estimates")
                        .param("origin", "JPOSA")
                        .param("destination", "USLAX")
                        .param("arrivalDeadline", 期限(60))
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1000")
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/estimates/")))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        String estimateId = location.substring(location.lastIndexOf('/') + 1);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM estimate WHERE CAST(estimate_id AS VARCHAR) = ?",
                Integer.class, estimateId);
        assertThat(count).as("**見積が保存されていること**").isEqualTo(1);

        // **送り先が実在することまで見る。** リダイレクトしただけでは、
        // 押した人が 404 に着くことがある（行き止まりを作らない）
        mockMvc.perform(get(location).with(user("sales1").roles("SALES")))
                .andExpect(status().isOk());
    }

    /** <strong>見つからない見積は 404 である。</strong> 500 にしない。 */
    @Test
    void 見つからない見積は404である() throws Exception {
        mockMvc.perform(get("/estimates/{id}", java.util.UUID.randomUUID())
                        .with(user("sales1").roles("SALES")))
                .andExpect(status().isNotFound());
    }

    /**
     * <strong>形式の違う ID でも 500 にしない。</strong>
     *
     * <p>URL を直接編集しただけで障害に見える状態を作らない。
     */
    @Test
    void 形式の違う見積IDは404である() throws Exception {
        mockMvc.perform(get("/estimates/{id}", "not-a-uuid")
                        .with(user("sales1").roles("SALES")))
                .andExpect(status().isNotFound());
    }

    /**
     * <strong>出発地と目的地が同じでは作れない</strong>（`ui_design.md` の検証）。
     *
     * <p>集約の不変条件（ビジネスルール 2）を画面でも弾く。
     * <strong>500 にせず、フォームに戻して理由を出す。</strong>
     */
    @Test
    void 出発地と目的地が同じなら作成できない() throws Exception {
        String html = mockMvc.perform(post("/estimates")
                        .param("origin", "JPOSA")
                        .param("destination", "JPOSA")
                        .param("arrivalDeadline", 期限(60))
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1000")
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("出発地と目的地");
    }

    /**
     * <strong>過去の期限では作れない</strong>（`ui_design.md`「希望期限は当日以降」）。
     *
     * <p>過ぎた期限の見積は、作った瞬間に期限切れである。
     */
    @Test
    void 過去の期限では作成できない() throws Exception {
        String html = mockMvc.perform(post("/estimates")
                        .param("origin", "JPOSA")
                        .param("destination", "USLAX")
                        .param("arrivalDeadline", 期限(-1))
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1000")
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("希望到着期限");
    }

    /**
     * <strong>受入基準 6: 危険物では申告の入力欄が出る。</strong>
     *
     * <p><strong>現物に触る人が気づけない状態にしない。</strong>
     */
    @Test
    void 危険物を選ぶと申告欄が出る() throws Exception {
        String html = mockMvc.perform(get("/estimates/new")
                        .param("cargoType", "HAZARDOUS")
                        .with(user("sales1").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("危険物");
        assertThat(html).contains("hazardClass");
    }

    /**
     * <strong>一般貨物では申告欄を出さない。</strong>
     *
     * <p>常に出す実装でも上のテストは緑になる —
     * <strong>出ることと出ないことの両方を見る。</strong>
     */
    @Test
    void 一般貨物では申告欄を出さない() throws Exception {
        String html = mockMvc.perform(get("/estimates/new")
                        .with(user("sales1").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("hazardClass");
    }

    /** <strong>営業担当者以外は作成できない。</strong> */
    @Test
    void 営業担当者以外は作成できない() throws Exception {
        mockMvc.perform(post("/estimates")
                        .param("origin", "JPOSA")
                        .param("destination", "USLAX")
                        .param("arrivalDeadline", 期限(60))
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1000")
                        .with(user("handler1").roles("HANDLER")).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
