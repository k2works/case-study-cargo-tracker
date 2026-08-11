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
 * 荷役作業一覧の<strong>ロール別の見え方</strong>（IT17 クローズ前レビュー H1）。
 *
 * <p>A1 で追跡管理者に一覧を開いたが、<strong>登録・申請は荷役作業員のままである</strong>。
 * 認可は 403 で守られているが、<strong>ボタンだけ出ていると、自分が承認した手配を
 * 確かめに来た人を行き止まりへ連れて行く</strong>。
 *
 * <p>「画面にボタンを出さないことは認可ではない」の<strong>裏返し</strong>である。
 * 出していないことが認可なのではなく、<strong>押せないものを出さない</strong>のが親切である。
 */
@DisplayName("荷役作業一覧のロール別の見え方（H1）")
@AutoConfigureMockMvc
class HandlingListRoleVisibilityTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * <strong>追跡管理者に押せないボタンを見せない</strong>（IT17 クローズ前レビュー H1）。
     *
     * <p>A1 で一覧を開いたが、<strong>「新規登録」「荷役を登録」「申請する」は
     * 荷役作業員のままである</strong>。ボタンだけ出ていると、押した瞬間に 403 になる。
     *
     * <p><strong>「画面にボタンを出さないことは認可ではない」の裏返しである。</strong>
     * 認可は 403 で守られているが、<strong>自分が承認した手配を確かめに来た
     * 追跡管理者を、行き止まりに連れて行く</strong>。A1 で開いた価値がそこで消える。
     */
    @Test
    void 追跡管理者に押せないボタンを見せない() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7501");
        承認する(bookingId, "JPTYO");

        String html = 荷役作業一覧("tracker1", "TRACKER");

        assertThat(html)
                .as("**押すと 403 になるボタンを出さないこと**")
                .doesNotContain(">新規登録<")
                .doesNotContain(">荷役を登録<")
                .doesNotContain(">申請する<");
    }

    /**
     * <strong>荷役作業員には従来どおりボタンが出る。</strong>
     *
     * <p>すべて隠す実装でも上のテストは緑になる —
     * <strong>出ることと出ないことの両方を見る。</strong>
     */
    @Test
    void 荷役作業員にはボタンが出る() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7502");
        承認する(bookingId, "JPTYO");

        String html = 荷役作業一覧("handler1", "HANDLER");

        assertThat(html)
                .contains(">新規登録<")
                .contains(">荷役を登録<");
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
                .shipperNamePrefix("ロール別表示テスト商事")
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
