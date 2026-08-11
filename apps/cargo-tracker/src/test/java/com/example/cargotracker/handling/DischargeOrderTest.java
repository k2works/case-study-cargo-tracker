package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

/**
 * 承認したキャンセルの陸揚げ地が荷役の現場に届く（US30 の受入基準。#515）。
 *
 * <p>US30 は「承認するとキャンセルが確定し、<strong>指定した陸揚げ地への荷降しが
 * 手配され</strong>、荷主に通知される」と定めている。IT15 で満たしたのは
 * 「荷主に通知される」までだった。承認の記録が残るのは<strong>予約詳細と荷主への通知</strong>
 * だけで、実際に船から降ろす荷役作業員が見る一覧には何も出ていなかった。
 *
 * <p><strong>降ろす人が知らないなら、手配したことにならない。</strong>
 *
 * <p><strong>画面から踏んで確かめる。</strong> ポートとクエリサービスの単体テストは
 * 「荷役作業員の画面に出るか」を判別しない（IT15 で学んだ形）。
 */
@AutoConfigureMockMvc
@DisplayName("荷降し手配が荷役の現場に届く（US30 / #515）")
class DischargeOrderTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * <strong>承認した陸揚げ地が荷役作業一覧に出る</strong>（デモ項目 1）。
     *
     * <p>これが US30 の唯一の未達受入基準だった。
     */
    @Test
    void 承認した陸揚げ地が荷役作業一覧に出る() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7001");
        承認する(bookingId, "JPTYO");

        String html = 荷役作業一覧("handler1", "HANDLER");

        assertThat(html)
                .as("**降ろす人に届いていること**")
                .contains("荷降し手配")
                .contains("TRK-20260811-7001")
                .contains("JPTYO");
    }

    /**
     * <strong>手配の行から荷役登録へ、追跡番号を持ったまま行ける</strong>（デモ項目 2）。
     *
     * <p><strong>気づく手段は次の行動へ繋ぐ。</strong> 一覧に出しても、そこから
     * 作業に移れなければ番号を読んで打ち直すことになる。
     */
    @Test
    void 手配の行から荷役登録へ追跡番号を持ったまま行ける() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7002");
        承認する(bookingId, "JPTYO");

        assertThat(荷役作業一覧("handler1", "HANDLER"))
                .as("一覧に登録への導線があること")
                .contains("/handling/new?trackingNumber=TRK-20260811-7002");

        String form = mockMvc.perform(get("/handling/new")
                        .param("trackingNumber", "TRK-20260811-7002")
                        .with(user("handler1").roles("HANDLER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(form)
                .as("**番号を打ち直させない**")
                .contains("TRK-20260811-7002");
    }

    /**
     * <strong>承認していない申請は手配に出ない</strong>（デモ項目 3）。
     *
     * <p>拒む側を確かめる。<strong>すべてを手配として出す実装で緑にしない。</strong>
     */
    @Test
    void 承認していない申請は手配に出ない() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7003");
        申請する(bookingId);

        assertThat(荷役作業一覧("handler1", "HANDLER"))
                .doesNotContain("TRK-20260811-7003");
    }

    /**
     * <strong>却下した申請は手配に出ない</strong>（デモ項目 4）。
     *
     * <p>却下は「降ろさない」という決定である。<strong>決着したことと
     * 手配があることは違う。</strong>
     */
    @Test
    void 却下した申請は手配に出ない() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7004");
        long requestId = 申請する(bookingId);
        mockMvc.perform(post("/bookings/cancellations/{id}/rejection", requestId)
                        .param("reason", "荷受人と調整中のため")
                        .with(user("tracker1").roles("TRACKER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(荷役作業一覧("handler1", "HANDLER"))
                .doesNotContain("TRK-20260811-7004");
    }

    /**
     * <strong>却下は、陸揚げ地が入っていても手配にしない</strong>（デモ項目 4 の判別）。
     *
     * <p><strong>この 1 本を足したのは、破壊検証が空振りしたためである。</strong>
     * ステータスの絞り込み（{@code status = 'APPROVED'}）を外しても、
     * 上の 2 本は緑のままだった。決着していない申請と却下した申請は
     * 陸揚げ地が {@code NULL} であり、{@code location} への JOIN が
     * <strong>たまたま同じ結果を出していた</strong>。
     *
     * <p>つまり守っていたのは JOIN であって、書いたつもりの規則ではない。
     * JOIN を {@code LEFT JOIN} に変えた瞬間、却下した貨物が現場の手配に出る。
     *
     * <p>DB の制約は「承認したなら陸揚げ地がある」だけを課しており、
     * <strong>却下に陸揚げ地が入っていることを禁じていない</strong>。
     * その形を作って、ステータスの絞り込みだけが立っている状態で確かめる。
     */
    @Test
    void 陸揚げ地が入っていても却下なら手配に出ない() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7007");
        long requestId = 申請する(bookingId);
        jdbcTemplate.update("""
                UPDATE booking_cancellation
                   SET status = 'REJECTED',
                       discharge_location_unlocode = 'JPTYO',
                       decided_by = 'tracker1',
                       decided_at = CURRENT_TIMESTAMP,
                       decision_reason = '荷受人と調整がついたため輸送を続ける'
                 WHERE id = ?
                """, requestId);

        assertThat(荷役作業一覧("handler1", "HANDLER"))
                .as("**降ろさないと決めたものを現場に流さない**")
                .doesNotContain("TRK-20260811-7007");
    }

    /**
     * <strong>荷降しを登録すると手配が一覧から消える</strong>（デモ項目 2b）。
     *
     * <p><strong>「荷降し手配」は指示であって記録ではない。</strong> 済んだ指示が
     * 残り続けると、現場は毎朝それを読み飛ばすようになり、やがて新しい指示も
     * 読み飛ばす。
     */
    @Test
    void 荷降しを登録すると手配が一覧から消える() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7005");
        承認する(bookingId, "JPTYO");
        assertThat(荷役作業一覧("handler1", "HANDLER")).contains("TRK-20260811-7005");

        荷降しを記録する(bookingId, "JPTYO");

        assertThat(荷役作業一覧("handler1", "HANDLER"))
                .as("**降ろし終えた手配は残さない**")
                .doesNotContain("TRK-20260811-7005");
    }

    /**
     * <strong>荷役作業員は荷降し手配に到達できる</strong>（ロール別の到達性）。
     *
     * <p><strong>追跡管理者については正典が食い違っている。</strong>
     * {@code ui_design.md} の画面一覧は荷役作業一覧を
     * 「{@code ROLE_HANDLER}, {@code ROLE_TRACKER}」と定めているが、
     * {@code SecurityConfig} は {@code /handling} を荷役作業員のみに絞り、
     * 「現場が使う唯一の画面である」と理由を書いている。
     * <strong>どちらかが古い。</strong>
     *
     * <p><strong>ここでは追跡管理者の到達性を主張しない。</strong> 認可を広げるのは
     * 判断が要る変更であり、テストで既成事実にしてよいものではない。
     * 食い違いは IT16 の設計反映として記録し、決着させてから検査に落とす。
     *
     * <p>US30 の受入基準が求めるのは<strong>荷降しを行う荷役作業員に届くこと</strong>
     * であり、これは満たされている。
     */
    @Test
    void 荷役作業員は荷降し手配に到達できる() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7006");
        承認する(bookingId, "JPTYO");

        assertThat(荷役作業一覧("handler1", "HANDLER")).contains("TRK-20260811-7006");
    }

    private String 荷役作業一覧(String username, String role) throws Exception {
        return mockMvc.perform(get("/handling").with(user(username).roles(role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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

    private void 承認する(UUID bookingId, String dischargeUnlocode) throws Exception {
        long requestId = 申請する(bookingId);
        mockMvc.perform(post("/bookings/cancellations/{id}/approval", requestId)
                        .param("discharge", dischargeUnlocode)
                        .with(user("tracker1").roles("TRACKER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    /** 現場が実際に降ろした記録（{@code UNLOAD}）。**手配とは別物である。** */
    private void 荷降しを記録する(UUID bookingId, String unlocode) {
        jdbcTemplate.update("""
                INSERT INTO handling_activity (
                    booking_id, event_type, event_completion_time,
                    location_unlocode, operator_name, version)
                VALUES (?, 'UNLOAD', CURRENT_TIMESTAMP, ?, 'handler1', 0)
                """, bookingId, unlocode);
    }

    /**
     * 輸送中の貨物を用意する。
     *
     * <p><strong>陸揚げ地の候補は「まだ着いていない揚地」である</strong>ため、
     * 区間の時刻を未来に置く。
     */
    private UUID 輸送中の貨物(String trackingNumber) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("荷降し手配テスト商事")
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
