package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.cargotracker.support.LogCapture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 業務操作ログが実際に出ていることを検証する（{@code non_functional.md} §4.4。IT1 持ち越し C4）。
 *
 * <p><strong>「ログを出す」とコードに書いてあることは、ログが出ていることを意味しない。</strong>
 * ロガー名の取り違え、レベルによる抑止、例外経路の素通りはいずれも
 * コードレビューでは見つけにくい。出力そのものを見る。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "sales", roles = "SALES")
// 到着期限の「当日」「過去」は現在日時に対する相対的な概念である。固定日で書くと
// **時間の経過とともにテストの意味が変わり、いずれ「過去の予約は登録できない」が
// すべての予約に当てはまってしまう。** ここでシステム時計を使うのは仕様そのものである。
// 時計に依存しない検証はユニットテスト（CargoTest）が担っている。
@SuppressWarnings("java:S8692")
class AuditLogTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                VALUES (?, ?, 'INDIVIDUAL', '監査テスト荷主', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """,
                UUID.randomUUID(), shipperCode, "audit-%s@example.com".formatted(shipperCode));
    }

    private Map<String, String> form() {
        Map<String, String> values = new HashMap<>();
        values.put("shipperCode", shipperCode);
        values.put("origin", "JPOSA");
        values.put("destination", "USLAX");
        values.put("arrivalDeadline", LocalDate.now().plusDays(30).toString());
        values.put("cargoType", "GENERAL");
        values.put("weight", "1000");
        return values;
    }

    private String 予約する() throws Exception {
        var req = post("/bookings").with(csrf());
        form().forEach(req::param);
        return mockMvc.perform(req).andReturn().getResponse().getRedirectedUrl()
                .replace("/bookings/", "");
    }

    @Test
    void 予約登録が誰の操作として記録される() throws Exception {
        try (LogCapture capture = LogCapture.of("audit.booking")) {
            String bookingId = 予約する();

            assertThat(capture.messages())
                    .as("誰が・何を・どの荷主に対して行ったかが揃っていないと監査に使えない")
                    .anySatisfy(message -> assertThat(message)
                            .contains("貨物予約登録")
                            .contains(bookingId)
                            .contains("actor=sales"));
        }
    }

    @Test
    void 予約キャンセルが誰の操作として記録される() throws Exception {
        String bookingId = 予約する();

        try (LogCapture capture = LogCapture.of("audit.booking")) {
            mockMvc.perform(post("/bookings/{id}/cancel", bookingId).with(csrf()));

            assertThat(capture.messages())
                    .anySatisfy(message -> assertThat(message)
                            .contains("貨物予約キャンセル")
                            .contains(bookingId)
                            .contains("actor=sales"));
        }
    }

    /**
     * 失敗した操作は業務操作ログに残さない。
     *
     * <p>**「行われなかった操作」が記録に残ると、監査ログは信用できなくなる。**
     */
    @Test
    void 登録に失敗した操作は記録されない() throws Exception {
        try (LogCapture capture = LogCapture.of("audit.booking")) {
            Map<String, String> values = form();
            values.put("shipperCode", "SHP-999999");
            var req = post("/bookings").with(csrf());
            values.forEach(req::param);
            mockMvc.perform(req);

            assertThat(capture.messages()).noneMatch(m -> m.contains("貨物予約登録"));
        }
    }
}
