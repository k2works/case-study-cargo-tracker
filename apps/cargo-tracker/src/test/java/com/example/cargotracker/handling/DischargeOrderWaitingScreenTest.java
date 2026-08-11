package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.CargoFixture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 荷降し手配の<strong>待ちの長さが画面に出る</strong>こと（IT17 の R1）。
 *
 * <p>規則そのものは {@code DischargeOrderWaitingTest} が DB 無しで確かめる。
 * <strong>ここで見るのは、それが画面まで届いているかである。</strong>
 * 集約や表示用データの単体テストは、<strong>画面での見え方を判別しない</strong>。
 */
@DisplayName("荷降し手配の待ちが画面に出る（R1）")
@AutoConfigureMockMvc
class DischargeOrderWaitingScreenTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * <strong>待ちが長い手配は画面で目立つ</strong>（IT17 の R1）。
     *
     * <p>IT16 の途中レビュー M2：出ているのは承認日時だけで、
     * <strong>古い順に並べても緊急度が分からなかった</strong>。
     *
     * <p><strong>期限ではなく経過を出す。</strong> 承認は「どこで降ろすか」を決めるが
     * 「いつまでに」は決めていない。無い期限を画面で作ると、守れなかったときに
     * 誰も責任を負えない数字になる。
     */
    @Test
    void 長く待っている手配は滞留として画面に出る() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7401");
        承認する(bookingId, "JPTYO");
        承認日時をずらす(bookingId, 24 * 10);

        String row = 手配の行(荷役作業一覧("handler1", "HANDLER"), "TRK-20260811-7401");

        assertThat(row)
                .as("**待ちの長さが読めること**")
                .contains("滞留")
                .contains("10 日");
    }

    /**
     * <strong>今日承認したものは滞留と呼ばない。</strong>
     *
     * <p>常に滞留と出す実装でも上のテストは緑になる —
     * <strong>出ることと出ないことの両方を見る。</strong>
     */
    @Test
    void 本日承認した手配は滞留と出ない() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7402");
        承認する(bookingId, "JPTYO");

        String row = 手配の行(荷役作業一覧("handler1", "HANDLER"), "TRK-20260811-7402");

        assertThat(row).contains("本日");
        assertThat(row)
                .as("本日の手配に滞留の印を付けないこと")
                .doesNotContain("滞留");
    }

    /**
     * 追跡番号を含む手配の行だけを取り出す。
     *
     * <p><strong>画面全体で判定しない。</strong> 同じ一覧には他のテストが作った手配も
     * 並ぶ。全体に「滞留」があることは、<strong>この行に滞留の印が付いていることを
     * 意味しない</strong> — 別の行の印を自分の成果と読み違える。
     */
    private String 手配の行(String html, String trackingNumber) {
        int found = html.indexOf(trackingNumber);
        assertThat(found).as("手配の行が見つからないなら、この検査は何も見ていない").isNotNegative();
        int start = html.lastIndexOf("<tr", found);
        int end = html.indexOf("</tr>", found);
        return html.substring(start, end);
    }


    private void 承認日時をずらす(UUID bookingId, int hoursAgo) {
        int updated = jdbcTemplate.update("""
                UPDATE booking_cancellation
                   SET decided_at = ?
                 WHERE booking_id = ? AND status = 'APPROVED'
                """,
                java.sql.Timestamp.from(
                        java.time.Instant.now().minusSeconds(3600L * hoursAgo)),
                bookingId);
        assertThat(updated).as("承認日時をずらせていること").isEqualTo(1);
    }

    private String 荷役作業一覧(String username, String role) throws Exception {
        return mockMvc.perform(get("/handling").with(user(username).roles(role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void 承認する(UUID bookingId, String dischargeUnlocode) throws Exception {
        long requestId = 申請する(bookingId);
        mockMvc.perform(post("/bookings/cancellations/{id}/approval", requestId)
                        .param("discharge", dischargeUnlocode)
                        .with(user("tracker1").roles("TRACKER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private long 申請する(UUID bookingId) throws Exception {
        mockMvc.perform(post("/bookings/{id}/cancellation", bookingId)
                        .param("reason", "荷主都合による中止")
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM booking_cancellation WHERE booking_id = ?",
                Long.class, bookingId);
        if (id == null) {
            throw new IllegalStateException("申請が残っていません: " + bookingId);
        }
        return id;
    }

    /** 輸送中の貨物を用意する（陸揚げ地の候補はまだ着いていない揚地である）。 */
    private UUID 輸送中の貨物(String trackingNumber) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("待ち日数テスト商事")
                .route("JPOSA", "USLAX")
                .status("IN_TRANSIT", "ROUTED")
                .trackingNumber(trackingNumber)
                .insert();
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0001', 'JPOSA', 'JPTYO',
                        CURRENT_TIMESTAMP + INTERVAL '1 day',
                        CURRENT_TIMESTAMP + INTERVAL '5 days', 1)
                """, cargo.cargoId());
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0002', 'JPTYO', 'USLAX',
                        CURRENT_TIMESTAMP + INTERVAL '6 days',
                        CURRENT_TIMESTAMP + INTERVAL '20 days', 2)
                """, cargo.cargoId());
        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'ONBOARD_CARRIER', 0)
                """, trackingNumber, cargo.bookingId());
        return cargo.bookingId();
    }
}
