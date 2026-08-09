package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 追跡照会に通関状態を出す（US29 / IT12 の C30）。
 *
 * <p><strong>引き取りに来る当人が通関状態を確認できない。</strong> IT11 は通関を
 * 荷役担当者の画面にだけ出した。荷受人は港へ着いてから「まだ通っていない」と
 * 知ることになる（IT11 レビュー C30）。
 *
 * <p>ここで確かめるのは<strong>「読めること」だけではない</strong>。
 * 通関が要らない貨物に通関の行を出さないこと（常に出す実装で緑にしない）、
 * <strong>申告がまだ無い国際貨物を黙って空欄にしないこと</strong>
 * （空欄は「問題なし」と読まれる）まで見る。
 *
 * <p>本画面は<strong>認証を持たない相手に見せる唯一の画面である</strong>。
 * 申告番号は税関に対する書類番号であり、追跡番号を知る全員に見せる理由がない。
 * <strong>状態は出し、書類番号は出さない。</strong>
 */
@AutoConfigureMockMvc
@DisplayName("追跡照会の通関状態（C30）")
class CustomsStatusOnInquiryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 荷降しまで進んだ貨物を用意する。 */
    private UUID 荷降し済みの貨物(String trackingNumber, String origin, String destination) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '通関表示テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq),
                "customs-view-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number, consignee_name)
                VALUES (?, ?, 'GENERAL', 1000, ?, ?, CURRENT_DATE + 60,
                        'IN_TRANSIT', 'ROUTED', ?, '受取花子')
                """, bookingId, shipperId, origin, destination, trackingNumber);

        Long cargoId = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0088', ?, ?,
                        TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-19 09:00:00+09', 1)
                """, cargoId, origin, destination);

        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version,
                    destination_unlocode, estimated_arrival_date)
                VALUES (?, ?, 'UNLOADED', 0, ?, DATE '2026-04-19')
                """, trackingNumber, bookingId, destination);
        return bookingId;
    }

    private void 申告を出す(String trackingNumber, String location) throws Exception {
        mockMvc.perform(post("/handling")
                .param("trackingNumber", trackingNumber)
                .param("type", "CUSTOMS")
                .param("completionTime", "2026-04-20T09:00")
                .param("locationUnlocode", location)
                .param("operatorName", "港湾太郎")
                .with(user("handler").roles("HANDLER")).with(csrf()));
        mockMvc.perform(post("/handling/customs")
                .param("trackingNumber", trackingNumber)
                .param("declarationNumber", 申告番号(trackingNumber))
                .param("declaredAt", "2026-04-20T09:30")
                .with(user("handler").roles("HANDLER")).with(csrf()));
    }

    /** 申告番号は追跡番号から作る。**固定値にすると他のテストと衝突する。** */
    private static String 申告番号(String trackingNumber) {
        return "DEC-" + trackingNumber.substring(trackingNumber.length() - 4);
    }

    private void 状態を変える(String trackingNumber, String status) throws Exception {
        long declarationId = jdbcTemplate.queryForObject("""
                SELECT d.id FROM customs_declaration d
                  JOIN handling_activity h ON h.id = d.handling_activity_id
                 WHERE h.tracking_number = ?
                """, Long.class, trackingNumber);
        mockMvc.perform(post("/handling/customs/{id}/status", declarationId)
                .param("status", status)
                .param("reason", "検査のため")
                .with(user("handler").roles("HANDLER")).with(csrf()));
    }

    private String 公開画面(String trackingNumber) throws Exception {
        return mockMvc.perform(get("/public/tracking/{n}", trackingNumber))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 通関済なら、引き取れることが読める。 */
    @Test
    void 通関済なら引き取れることが読める() throws Exception {
        荷降し済みの貨物("TRK-20260420-9601", "KRPUS", "USSEA");
        申告を出す("TRK-20260420-9601", "USSEA");
        状態を変える("TRK-20260420-9601", "CLEARED");

        assertThat(公開画面("TRK-20260420-9601"))
                .contains("通関")
                .contains("通関済")
                .contains("引き取れます");
    }

    /**
     * 留置なら、<strong>引き取れないことが読める</strong>。
     *
     * <p>状態名だけを出すと「留置」の意味が伝わらない。
     * <strong>港まで来てから知る</strong>のを避けるのがこの画面の目的である。
     */
    @Test
    void 留置なら引き取れないことが読める() throws Exception {
        荷降し済みの貨物("TRK-20260420-9602", "KRPUS", "USSEA");
        申告を出す("TRK-20260420-9602", "USSEA");
        状態を変える("TRK-20260420-9602", "HELD");

        assertThat(公開画面("TRK-20260420-9602"))
                .contains("留置")
                .contains("引き取れません");
    }

    /**
     * <strong>申告がまだ無い国際貨物を空欄にしない。</strong>
     *
     * <p>空欄は「問題なし」と読まれる。通関が要る貨物で手続きが始まっていないことは、
     * 荷受人にとって<strong>もっとも知りたい状態</strong>である。
     */
    @Test
    void 申告がまだ無い国際貨物は手続き前と読める() throws Exception {
        荷降し済みの貨物("TRK-20260420-9603", "KRPUS", "USSEA");

        assertThat(公開画面("TRK-20260420-9603"))
                .contains("通関")
                .contains("手続き前")
                .contains("引き取れません");
    }

    /**
     * <strong>国内輸送には通関の行を出さない。</strong>
     *
     * <p>これが無いと、常に通関の行を出す実装でも上の 3 件が緑になる。
     * その実装だと<strong>国内輸送の荷受人に、無関係な「手続き前」が出続ける</strong>。
     */
    @Test
    void 国内輸送には通関の行を出さない() throws Exception {
        荷降し済みの貨物("TRK-20260420-9604", "JPOSA", "JPTYO");

        assertThat(公開画面("TRK-20260420-9604"))
                .doesNotContain("通関");
    }

    /**
     * <strong>申告番号は公開画面に出さない。</strong>
     *
     * <p>税関に対する書類番号であり、追跡番号を知る全員に見せる理由がない。
     * 状態を足すときに書類番号まで一緒に運ばないことを、ここで固定する。
     */
    @Test
    void 申告番号は公開画面に出ない() throws Exception {
        荷降し済みの貨物("TRK-20260420-9605", "KRPUS", "USSEA");
        申告を出す("TRK-20260420-9605", "USSEA");

        assertThat(公開画面("TRK-20260420-9605"))
                .doesNotContain(申告番号("TRK-20260420-9605"));
    }
}
