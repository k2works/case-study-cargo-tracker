package com.example.cargotracker.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 引取による配送完了（US16。遷移表 #7）。
 *
 * <p><strong>IT6 で一本つながった線の終点である。</strong> 受領・積込・荷降しまでは
 * 記録できても、引き渡しを記録する手段が無かった。
 *
 * <p>ここで壊すのは「入口と出口の両方」である（IT6 ふりかえり T1）。
 * 引取確認の判定（入口）だけでなく、<strong>その結果が {@code cargo.booking_status} に
 * 書かれること（出口）</strong>まで確かめる。
 */
@AutoConfigureMockMvc
@DisplayName("引取による配送完了（US16）")
class CargoClaimScenarioTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 輸送中まで進めた貨物を用意する（引取の前提）。 */
    private String 輸送中の貨物(String trackingNumber, String consigneeName) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '山田物産株式会社', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """,
                shipperId, "SHP-%06d".formatted(seq), "claim-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number, consignee_name)
                VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'USLAX', CURRENT_DATE + 60,
                        'IN_TRANSIT', 'ROUTED', ?, ?)
                """, bookingId, shipperId, trackingNumber, consigneeName);

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
                    tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'UNLOADED', 0)
                """, trackingNumber, bookingId);
        return bookingId.toString();
    }

    private org.springframework.test.web.servlet.ResultActions 引取を登録する(
            String trackingNumber, String code, String consigneeName) throws Exception {
        var request = post("/handling")
                .param("trackingNumber", trackingNumber)
                .param("type", "CLAIM")
                .param("completionTime", "2026-04-20T10:00")
                .param("locationUnlocode", "USLAX")
                .param("operatorName", "港湾太郎")
                .with(user("handler").roles("HANDLER")).with(csrf());
        if (code != null) {
            request = request.param("confirmationCode", code);
        }
        if (consigneeName != null) {
            request = request.param("consigneeName", consigneeName);
        }
        return mockMvc.perform(request);
    }

    private String 予約状態(String bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT booking_status FROM cargo WHERE booking_id = ?",
                String.class, UUID.fromString(bookingId));
    }

    /** 受入基準: 荷受人確認が取得されると引取作業が記録され、状態が「引取済」になる。 */
    @Test
    void 引取を登録すると配送完了になる() throws Exception {
        String bookingId = 輸送中の貨物("TRK-20260420-7001", "受取花子");

        引取を登録する("TRK-20260420-7001", "123456", "受取花子")
                .andExpect(redirectedUrl("/handling"));

        // 出口: 荷役の記録が残る
        Integer handled = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM handling_activity
                 WHERE booking_id = ? AND event_type = 'CLAIM'
                """, Integer.class, UUID.fromString(bookingId));
        assertThat(handled).isEqualTo(1);

        // 出口: 予約が配送完了になる（遷移表 #7）
        assertThat(予約状態(bookingId)).isEqualTo("DELIVERED");

        // 出口: 輸送状態が引取完了になる
        String transport = jdbcTemplate.queryForObject(
                "SELECT transport_status FROM tracking_activity WHERE tracking_number = ?",
                String.class, "TRK-20260420-7001");
        assertThat(transport).isEqualTo("CLAIMED");
    }

    /**
     * <strong>確認の無い引取は登録できない。</strong> 荷役は原則として
     * 「予定と違っても記録する」が、証明の無い引き渡しを「引き渡し済」として
     * 残すほうが害が大きい。
     */
    @Test
    void 確認のない引取は登録できず状態も動かない() throws Exception {
        String bookingId = 輸送中の貨物("TRK-20260420-7002", "受取花子");

        引取を登録する("TRK-20260420-7002", null, null)
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("handlingForm"));

        Integer handled = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handling_activity WHERE booking_id = ?",
                Integer.class, UUID.fromString(bookingId));
        assertThat(handled).isZero();
        assertThat(予約状態(bookingId)).isEqualTo("IN_TRANSIT");
    }

    /**
     * <strong>代理受領は拒否しない。</strong> 実務で頻繁に起きる。
     * 予約の荷受人と違う氏名でも記録は残り、状態も進む。
     */
    @Test
    void 予約の荷受人と違う人が受け取っても登録できる() throws Exception {
        String bookingId = 輸送中の貨物("TRK-20260420-7003", "受取花子");

        引取を登録する("TRK-20260420-7003", "123456", "代理次郎")
                .andExpect(redirectedUrl("/handling"));

        assertThat(予約状態(bookingId)).isEqualTo("DELIVERED");
        String recorded = jdbcTemplate.queryForObject("""
                SELECT claim_consignee_name FROM handling_activity
                 WHERE booking_id = ? AND event_type = 'CLAIM'
                """, String.class, UUID.fromString(bookingId));
        // **実際に受け取った人を残す。** 予約の荷受人で上書きすると、
        // 誰が受け取ったかが記録から消える
        assertThat(recorded).isEqualTo("代理次郎");
    }

    /**
     * <strong>引取は 2 度目でも記録は残るが、状態は動かない。</strong>
     * 二重登録は現場で起きうるが、それで状態が壊れてはならない。
     */
    @Test
    void 引取を二重登録しても状態は壊れない() throws Exception {
        String bookingId = 輸送中の貨物("TRK-20260420-7004", "受取花子");

        引取を登録する("TRK-20260420-7004", "123456", "受取花子");
        引取を登録する("TRK-20260420-7004", "123456", "受取花子");

        Integer handled = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM handling_activity
                 WHERE booking_id = ? AND event_type = 'CLAIM'
                """, Integer.class, UUID.fromString(bookingId));
        assertThat(handled).isEqualTo(2);
        assertThat(予約状態(bookingId)).isEqualTo("DELIVERED");
    }

    /**
     * <strong>DB でも守る。</strong> 画面のバリデーションだけに頼らない。
     * 画面を経由しない登録経路（将来の API・データ移行）でも守られる必要がある。
     */
    @Test
    void 確認のない引取はDBが受け付けない() {
        String bookingId = 輸送中の貨物("TRK-20260420-7005", "受取花子");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO handling_activity (
                    booking_id, event_type, event_completion_time,
                    location_unlocode, tracking_number, version)
                VALUES (?, 'CLAIM', CURRENT_TIMESTAMP, 'USLAX', ?, 0)
                """, UUID.fromString(bookingId), "TRK-20260420-7005"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
