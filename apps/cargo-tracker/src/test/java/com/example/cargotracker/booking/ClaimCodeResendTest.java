package com.example.cargotracker.booking;

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
 * 引取確認コードを荷主に再度伝える（US35 / IT12 持ち越し C7）。
 *
 * <p>荷受人がコードを忘れて港に来ることは起きる。IT12 は画面に
 * 「担当営業へお問い合わせください」と案内を足したが、
 * <strong>問い合わせを受けた営業に伝える手段が無い</strong>。案内した先が
 * 行き止まりになっている。
 *
 * <p><strong>再発行はしない。</strong> 発行し直すと、元のコードを持って港に来た
 * 荷受人が弾かれる。<strong>忘れた人を助ける操作が、覚えていた人を締め出す</strong>。
 * 伝えるのは<strong>いま有効なコードそのもの</strong>である。
 *
 * <p>伝えた事実は通知の記録に残す（ADR-006 により外部へは送らない）。
 * <strong>「伝えたつもり」を後から検知できることが記録の目的である。</strong>
 */
@AutoConfigureMockMvc
@DisplayName("引取確認コードの再伝達（C7）")
class ClaimCodeResendTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 確定済み（引取確認コードが採番されている）の貨物を用意する。 */
    private UUID 確定済みの貨物(String trackingNumber, String claimCode) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '再伝達テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq),
                "resend-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number, claim_code)
                VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'USLAX', CURRENT_DATE + 60,
                        'IN_TRANSIT', 'ROUTED', ?, ?)
                """, bookingId, shipperId, trackingNumber, claimCode);
        return bookingId;
    }

    private String 予約詳細(UUID bookingId) throws Exception {
        return mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * <strong>営業担当者が予約詳細から再度伝えられる。</strong>
     *
     * <p>伝えた事実が通知履歴に残る。<strong>「伝えたつもり」を後から検知できる。</strong>
     */
    @Test
    void 営業担当者が引取確認コードを再度伝えられる() throws Exception {
        UUID bookingId = 確定済みの貨物("TRK-20260501-7701", "CLM-A1B2C3D1");

        mockMvc.perform(post("/bookings/{id}/notifications/claim-code", bookingId)
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(予約詳細(bookingId))
                .as("伝えた事実が通知履歴に残る")
                .contains("引取確認コード");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_notification
                 WHERE booking_id = ? AND notification_type = 'CLAIM_CODE_RESENT'
                """, Integer.class, bookingId))
                .isEqualTo(1);
    }

    /**
     * <strong>再発行はしない。</strong>
     *
     * <p>発行し直すと、元のコードを持って港に来た荷受人が弾かれる。
     * 忘れた人を助ける操作が、覚えていた人を締め出す。
     */
    @Test
    void 再伝達してもコードは変わらない() throws Exception {
        UUID bookingId = 確定済みの貨物("TRK-20260501-7702", "CLM-A1B2C3D2");

        mockMvc.perform(post("/bookings/{id}/notifications/claim-code", bookingId)
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT claim_code FROM cargo WHERE booking_id = ?", String.class, bookingId))
                .isEqualTo("CLM-A1B2C3D2");
    }

    /**
     * <strong>コードが無い予約では伝えられない。</strong>
     *
     * <p>確定前の予約にはコードが無い。空の通知を積むと、
     * <strong>「伝えた記録があるのに中身が無い」</strong>という最も困る形になる。
     */
    @Test
    void コードが無い予約では再伝達できない() throws Exception {
        UUID bookingId = 確定済みの貨物("TRK-20260501-7703", null);

        mockMvc.perform(post("/bookings/{id}/notifications/claim-code", bookingId)
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_notification
                 WHERE booking_id = ? AND notification_type = 'CLAIM_CODE_RESENT'
                """, Integer.class, bookingId))
                .as("中身の無い通知を積まない")
                .isZero();
    }

    /**
     * <strong>荷主は自分では再伝達できない。</strong>
     *
     * <p>コードは「受け取ってよい人か」を確かめる秘密の値である。
     * <strong>伝えるのは営業の仕事である</strong>（誰に伝えたかの記録が残る）。
     */
    @Test
    void 荷主は再伝達を実行できない() throws Exception {
        UUID bookingId = 確定済みの貨物("TRK-20260501-7704", "CLM-A1B2C3D4");

        mockMvc.perform(post("/bookings/{id}/notifications/claim-code", bookingId)
                        .with(user("shipper1").roles("SHIPPER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * <strong>操作の入口がある。</strong>
     *
     * <p>「気づく手段」だけでは仕事は進まない。押せる場所が予約詳細に無ければ、
     * 営業は問い合わせを受けても何もできない。
     */
    @Test
    void 予約詳細に再伝達の入口がある() throws Exception {
        UUID bookingId = 確定済みの貨物("TRK-20260501-7705", "CLM-A1B2C3D5");

        assertThat(予約詳細(bookingId)).contains("/notifications/claim-code");
    }
}
