package com.example.cargotracker.billing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 経理担当者に何を見せ、何を見せないか（US21 / US22。レビュー H2）。
 *
 * <p><strong>請求書は金額である。</strong> 見える範囲を誤ると他社の取引条件が漏れる。
 * 一方で<strong>見せなさすぎても仕事が止まる</strong> — マニュアル 11.3 と運用要件 R1 は
 * 「料金調整は例外の記録を見ながら判断する」と書いているが、
 * <strong>経理担当者は予約詳細を開けず、その作業ができなかった</strong>。
 *
 * <p>請求対象一覧の「例外あり」は<strong>気づく手段</strong>にすぎない。そこから中身へ
 * 行けなければ、減額の判断は電話と口頭の数字になり、
 * <strong>後から根拠を追えない請求書</strong>が積み上がる。
 *
 * <p><strong>本クラスは {@code ChargeCalculationScenarioTest} から分けた。</strong>
 * 金額を算出する業務シナリオと、<strong>誰に何を見せるか</strong>は別の関心である
 * （元クラスが 500 行の上限に当たったのが合図だった）。
 */
@AutoConfigureMockMvc
@DisplayName("請求の到達性と認可（US21 / US22）")
class BillingVisibilityTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 引取まで済んだ貨物を用意する。 */
    private UUID 引取済みの貨物(String trackingNumber) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street, discount_rate)
                VALUES (?, ?, 'INDIVIDUAL', '到達性テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1', 0)
                """, shipperId, "SHP-%06d".formatted(seq),
                "visibility-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number)
                VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'USLAX', CURRENT_DATE + 60,
                        'DELIVERED', 'ROUTED', ?)
                """, bookingId, shipperId, trackingNumber);

        Long cargoId = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0001', 'JPOSA', 'USLAX',
                        TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-20 09:00:00+09', 1)
                """, cargoId);
        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version,
                    destination_unlocode, estimated_arrival_date)
                VALUES (?, ?, 'CLAIMED', 0, 'USLAX', DATE '2026-04-20')
                """, trackingNumber, bookingId);
        return bookingId;
    }

    private String 料金を算出する(UUID bookingId) throws Exception {
        String location = mockMvc.perform(post("/billing/invoices")
                        .param("bookingId", bookingId.toString())
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        return location == null ? "" : location.substring(location.lastIndexOf('/') + 1);
    }

    /**
     * <strong>経理担当者が料金調整の判断材料に到達できる</strong>（レビュー H2）。
     *
     * <p>書いた運用が実装で成立していなかった形である。
     */
    @Test
    void 経理担当者が例外の記録に到達できる() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260602-6001");

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk());
    }

    /**
     * <strong>請求書の画面で例外の中身が読める</strong>（IT13 レビュー C3）。
     *
     * <p>減額をいくらにするかを決めるのは、この画面を開いている人である。
     * <strong>「例外あり」だけでは金額を決められない。</strong> 別の画面へ探しに行く間に、
     * 経理担当者は「たぶん遅延だろう」で数字を入れてしまい、
     * <strong>後から根拠を追えない請求書</strong>が残る。
     */
    @Test
    void 請求書に例外の中身が出る() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260602-6004");
        例外を記録する("TRK-20260602-6004", "DELAY", "台風で 3 日遅延した");
        String invoiceNumber = 料金を算出する(bookingId);

        String html = mockMvc.perform(get("/billing/invoices/{n}", invoiceNumber)
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("**何が起きたのかが、金額を入れる画面に出ている**")
                .contains("台風で 3 日遅延した")
                .contains("遅延");
    }

    /**
     * <strong>例外が無い請求書でも画面は開ける</strong>（C3）。
     *
     * <p>これが無いと、常に見出しを出す実装でも上のテストが緑になる。
     */
    @Test
    void 例外が無ければ例外の欄を出さない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260602-6005");
        String invoiceNumber = 料金を算出する(bookingId);

        String html = mockMvc.perform(get("/billing/invoices/{n}", invoiceNumber)
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("**無いものの見出しを出さない。** 空の枠は「まだ読み込み中」に見える")
                .doesNotContain("この貨物の例外");
    }

    /** 例外を 1 件記録する。 */
    private void 例外を記録する(String trackingNumber, String type, String description) {
        Long trackingId = jdbcTemplate.queryForObject(
                "SELECT id FROM tracking_activity WHERE tracking_number = ?",
                Long.class, trackingNumber);
        jdbcTemplate.update("""
                INSERT INTO tracking_exception_event (
                    tracking_id, exception_type, occurred_at,
                    status_before, description)
                VALUES (?, ?, TIMESTAMP WITH TIME ZONE '2026-04-18 09:00:00+09',
                        'UNLOADED', ?)
                """, trackingId, type, description);
    }

    /**
     * <strong>経理担当者は予約を操作できない。</strong>
     *
     * <p>読めることと操作できることを混ぜない。開くのは
     * <strong>料金調整の判断材料を読むため</strong>であり、予約を動かすためではない。
     */
    @Test
    void 経理担当者は予約を操作できない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260602-6002");

        mockMvc.perform(post("/bookings/{id}/consignee", bookingId)
                        .param("consigneeName", "勝手に変更")
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * <strong>請求書は経理担当者にしか見せない。</strong>
     *
     * <p>金額であり、見える範囲を誤ると他社の取引条件が漏れる。
     * <strong>請求書が存在する状態で確かめる</strong> — 空の一覧では判別しない。
     */
    @Test
    void 経理担当者以外は請求書を開けない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260602-6003");
        String invoiceNumber = 料金を算出する(bookingId);

        mockMvc.perform(get("/billing/invoices/{n}", invoiceNumber)
                        .with(user("sales1").roles("SALES")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/billing/pending").with(user("shipper1").roles("SHIPPER")))
                .andExpect(status().isForbidden());
    }
}
