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
 * 見積から予約への引き継ぎ（US01。`ui_design.md` の画面遷移図）。
 *
 * <p><strong>この導線が無いと、営業担当者は見積で入力した内容を予約登録で
 * もう一度入力することになる。</strong>
 *
 * <p><strong>期限切れの見積からは進めない。</strong> 過ぎた期限で予約を作ると、
 * 作った瞬間に間に合わない予約になる。
 */
@DisplayName("見積から予約への引き継ぎ（US01）")
@AutoConfigureMockMvc
class EstimateToBookingTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String 見積を作る(int deadlineInDays) throws Exception {
        MvcResult result = mockMvc.perform(post("/estimates")
                        .param("origin", "JPOSA")
                        .param("destination", "USLAX")
                        .param("arrivalDeadline",
                                LocalDate.now().plusDays(deadlineInDays).toString())
                        .param("cargoType", "REFRIGERATED")
                        .param("weightKg", "1500")
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
     * <strong>詳細に「この見積で予約する」がある。</strong>
     *
     * <p>気づく手段は次の行動へ繋ぐ。
     */
    @Test
    void 見積詳細から予約へ進める() throws Exception {
        String html = 詳細(見積を作る(60));

        assertThat(html).contains("この見積で予約する");
        assertThat(html).contains("/bookings/new");
    }

    /**
     * <strong>予約登録に見積の内容が入った状態で開く。</strong>
     *
     * <p><strong>リンクがあることと、内容が引き継がれることは別である。</strong>
     * 遷移先で空のフォームが出るなら、この導線は無いのと同じである。
     */
    @Test
    void 予約登録に見積の内容が入っている() throws Exception {
        String deadline = LocalDate.now().plusDays(60).toString();

        String html = mockMvc.perform(get("/bookings/new")
                        .param("origin", "JPOSA")
                        .param("destination", "USLAX")
                        .param("arrivalDeadline", deadline)
                        .param("cargoType", "REFRIGERATED")
                        .param("weight", "1500")
                        .with(user("sales1").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("**同じ条件を 2 度入力させない**")
                .contains("value=\"JPOSA\"")
                .contains("value=\"USLAX\"")
                .contains("value=\"" + deadline + "\"")
                .contains("value=\"1500\"");
    }

    /**
     * <strong>期限切れの見積からは予約へ進めない</strong>（`ui_design.md`）。
     *
     * <p>過ぎた期限で予約を作ると、作った瞬間に間に合わない予約になる。
     */
    @Test
    void 期限切れの見積からは予約へ進めない() throws Exception {
        String location = 見積を作る(1);
        String estimateId = location.substring(location.lastIndexOf('/') + 1);
        // **期限を過ぎさせる。** 判定は読み出しのたびに行う（ビジネスルール 7）
        jdbcTemplate.update("""
                UPDATE estimate SET arrival_deadline = CURRENT_DATE - 1
                 WHERE CAST(estimate_id AS VARCHAR) = ?
                """, estimateId);

        String html = 詳細(location);

        assertThat(html).contains("有効期限を過ぎています");
        assertThat(html)
                .as("**押せないボタンを出さない**（IT17 の Try T1）")
                .doesNotContain("この見積で予約する");
    }
}
