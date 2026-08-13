package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 予約登録フォームの往復（IT18 の Try T1）。
 *
 * <p><strong>「入力欄がある」ことと「保存される」ことは別である。</strong>
 * IT18 では危険物の申告欄を出しながら保存していなかった。テストは
 * <strong>欄が出るか</strong>しか見ておらず、<strong>入れた値がどこへ行ったかを
 * 見ていなかった</strong>。
 *
 * <p>入れた値がすべて詳細に出るところまでを 1 本で確かめる。
 * <strong>捨てている欄があれば、ここで赤くなる。</strong>
 */
@AutoConfigureMockMvc
@WithMockUser(username = "sales", roles = "SALES")
@DisplayName("予約登録は入力を往復する（T1）")
class BookingFormRoundTripTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private String shipperCode;

    @BeforeEach
    void 荷主を用意する() {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        shipperCode = "SHP-%06d".formatted(seq);
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '往復商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, UUID.randomUUID(), shipperCode,
                "roundtrip-%d@example.com".formatted(seq));
    }

    /**
     * <strong>入れた値がすべて詳細に出る。</strong>
     *
     * <p>寸法・個数・品名は必須ではない。<strong>必須でない欄ほど、捨てても
     * 誰も気づかない</strong>。
     */
    @Test
    void 入力した内容がすべて詳細に出る() throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("shipperCode", shipperCode);
        form.put("cargoType", "GENERAL");
        form.put("weight", "1234.5");
        form.put("origin", "JPOSA");
        form.put("destination", "USLAX");
        String deadline = LocalDate.now(clock).plusDays(40).toString();
        form.put("arrivalDeadline", deadline);
        form.put("dimensionLength", "120");
        form.put("dimensionWidth", "80");
        form.put("dimensionHeight", "95");
        form.put("quantity", "7");
        form.put("description", "往復の検査に使う貨物");

        String html = 登録して詳細を開く(form);

        assertThat(html)
                .as("**入れた値を捨てていないこと**（IT18 の T1）")
                .contains("JPOSA")
                .contains("USLAX")
                .contains(deadline)
                .contains("1234.5")
                // **描画された形で見る**（クローズ前レビュー）。裸の数字を探すと、
                // 日付・UUID・ページ番号に当たって**捨てていても緑**になる
                .contains("120 × 80 × 95 cm")
                .contains("往復の検査に使う貨物");
        assertThat(個数の表示(html))
                .as("**個数を捨てていないこと**")
                .isEqualTo("7");
    }

    /**
     * <strong>危険物の申告も往復する</strong>（US05）。
     *
     * <p>IT18 で見積の側が同じ形で捨てていた。<strong>同じ型の失敗は繰り返す。</strong>
     */
    @Test
    void 危険物の申告が詳細に出る() throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("shipperCode", shipperCode);
        form.put("cargoType", "HAZARDOUS");
        form.put("weight", "500");
        form.put("origin", "JPKOB");
        form.put("destination", "NLRTM");
        form.put("arrivalDeadline", LocalDate.now(clock).plusDays(50).toString());
        form.put("hazardClass", "3");
        form.put("unNumber", "UN1263");
        form.put("properShippingName", "PAINT");

        String html = 登録して詳細を開く(form);

        assertThat(html)
                .as("**申告の無い危険物を預かる形を作らない**（US05）")
                .contains("UN1263")
                .contains("PAINT");
    }

    /**
     * 詳細画面の「個数」欄の値。
     *
     * <p><strong>裸の数字を HTML 全体から探さない。</strong> ページには日付・UUID・
     * ページ番号があり、{@code contains("7")} はほぼ常に真になる ——
     * <strong>捨てていても緑になる</strong>（クローズ前レビュー）。
     */
    private String 個数の表示(String html) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("個数</dt>\\s*<dd[^>]*>([^<]*)</dd>")
                .matcher(html);
        return matcher.find() ? matcher.group(1).strip() : "（見つかりません）";
    }

    private String 登録して詳細を開く(Map<String, String> form) throws Exception {
        var request = post("/bookings").with(csrf());
        form.forEach(request::param);
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // **PRG のリダイレクト先をそのまま辿る。** 画面を触る人と同じ道を通る
        return mockMvc.perform(get(result.getResponse().getHeader("Location")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
