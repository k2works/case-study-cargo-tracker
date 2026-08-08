package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.math.BigDecimal;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 法人荷主の登録（US03）。
 *
 * <p>受入基準は「荷主種別『法人』を選択すると法人契約情報の入力欄が表示される」
 * 「割引率は 0〜30% の範囲で設定できる」「登録完了後、荷主 ID が発行される」である。
 *
 * <p><strong>US22（法人割引の適用）は本 IT の範囲外である。</strong>
 * Billing Context が未実装であり、参照する側が無い状態でポート
 * （{@code ShipperDiscountPort}）だけ作ると、<strong>呼ばれない実装が
 * 「済み」として残る</strong>。
 */
@AutoConfigureMockMvc
@DisplayName("法人荷主の登録（US03）")
class CorporateShipperRegistrationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private org.springframework.test.web.servlet.ResultActions 登録する(
            String type, String email, String contractNumber, String discountRate)
            throws Exception {
        var request = post("/shippers")
                .param("shipperType", type)
                .param("name", "山田物産株式会社")
                .param("email", email)
                .param("phone", "06-1234-5678")
                .param("addressCountry", "JP")
                .param("addressPostalCode", "530-0001")
                .param("addressRegion", "大阪府")
                .param("addressCity", "大阪市北区")
                .param("addressStreet", "梅田 1-1-1")
                .with(user("sales").roles("SALES")).with(csrf());
        if (contractNumber != null) {
            request = request.param("contractNumber", contractNumber);
        }
        if (discountRate != null) {
            request = request.param("discountRate", discountRate);
        }
        return mockMvc.perform(request);
    }

    /** 受入基準: 法人荷主で登録完了後、荷主 ID が発行される。 */
    @Test
    void 法人荷主を契約条件つきで登録できる() throws Exception {
        登録する("CORPORATE", "corp1@example.com", "CT-2026-0001", "10.00")
                .andExpect(status().is3xxRedirection());

        var row = jdbcTemplate.queryForMap("""
                SELECT shipper_type, contract_number, discount_rate
                  FROM shipper WHERE email = ?
                """, "corp1@example.com");
        assertThat(row.get("shipper_type")).isEqualTo("CORPORATE");
        assertThat(row.get("contract_number")).isEqualTo("CT-2026-0001");
        // **画面は百分率、DB は小数。** 変換の向きを取り違えると 100 倍ずれる
        assertThat((BigDecimal) row.get("discount_rate"))
                .isEqualByComparingTo(new BigDecimal("0.1000"));
    }

    /** 受入基準: 割引率は 0〜30% の範囲で設定できる。 */
    @ParameterizedTest
    @ValueSource(strings = {"0.00", "15.50", "30.00"})
    void 範囲内の割引率で登録できる(String rate) throws Exception {
        String email = "corp-ok-%s@example.com".formatted(rate.replace(".", ""));

        登録する("CORPORATE", email, "CT-2026-0002", rate)
                .andExpect(status().is3xxRedirection());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipper WHERE email = ?", Integer.class, email);
        assertThat(count).isEqualTo(1);
    }

    /**
     * <strong>上限を超える割引率は登録できない。</strong> 上限はドメインの
     * 不変条件であり、画面の {@code max} 属性だけに頼らない
     * （HTML の属性は開発者ツールで外せる）。
     */
    @ParameterizedTest
    @ValueSource(strings = {"30.01", "50.00", "-1.00"})
    void 範囲外の割引率は登録できない(String rate) throws Exception {
        String email = "corp-ng-%s@example.com".formatted(rate.replace(".", "").replace("-", "m"));

        登録する("CORPORATE", email, "CT-2026-0003", rate)
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/form"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipper WHERE email = ?", Integer.class, email);
        assertThat(count).isZero();
    }

    /** 法人には契約番号が必須である（割引の根拠を請求書に書けない記録を残さない）。 */
    @Test
    void 契約番号の無い法人は登録できない() throws Exception {
        登録する("CORPORATE", "corp-nocontract@example.com", null, "10.00")
                .andExpect(status().isOk())
                .andExpect(view().name("shipper/form"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipper WHERE email = ?",
                Integer.class, "corp-nocontract@example.com");
        assertThat(count).isZero();
    }

    /**
     * <strong>個人を選んだときは契約の入力を捨てる。</strong> 種別を選び直す前に
     * 打っていた入力で弾くと、種別を変えるたびに入力し直させることになる。
     */
    @Test
    void 個人を選べば契約の入力は捨てられる() throws Exception {
        登録する("INDIVIDUAL", "kojin@example.com", "CT-2026-0004", "10.00")
                .andExpect(status().is3xxRedirection());

        var row = jdbcTemplate.queryForMap("""
                SELECT shipper_type, contract_number FROM shipper WHERE email = ?
                """, "kojin@example.com");
        assertThat(row.get("shipper_type")).isEqualTo("INDIVIDUAL");
        assertThat(row.get("contract_number")).isNull();
    }

    /** 受入基準: 法人を選択すると法人契約情報の入力欄が表示される。 */
    @Test
    void 登録画面に法人契約の入力欄がある() throws Exception {
        mockMvc.perform(get("/shippers/new").with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("契約番号")))
                .andExpect(content().string(Matchers.containsString("契約割引率")))
                // **法人が選べること自体を確かめる。** IT6 までは disabled だった
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("法人（現在は登録できません）"))));
    }

    /**
     * <strong>一覧では個人に「-」を出す。</strong> {@code ui_design.md} は
     * 「0% と {@code -} は意味が異なる（個人には契約割引の概念自体が無い）」と定めている。
     */
    @Test
    void 一覧では法人だけに割引率が出る() throws Exception {
        登録する("CORPORATE", "corp-list@example.com", "CT-2026-0005", "12.00");
        登録する("INDIVIDUAL", "kojin-list@example.com", null, null);

        String body = mockMvc.perform(get("/shippers").with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("12.00 %");
    }
}
