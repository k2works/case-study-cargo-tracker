package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * 予約詳細で「引取の訂正・取り消しの申請中」を読む（US36 / IT12 の C8）。
 *
 * <p><strong>承認待ちの間、貨物は「配送完了」のままである。</strong> 取り消しが
 * 申請されていても予約詳細には何も出ず、荷主から「まだ届いていない」と電話を
 * 受けた営業担当者は<strong>配送完了としか答えられない</strong>（IT12 持ち越し C8）。
 *
 * <p><strong>読み取り専用である。</strong> 承認・却下は追跡管理者の仕事であり、
 * ここでは動かさない。<strong>読めることと操作できることを混ぜない</strong>
 * （C31 で例外に対して下したのと同じ判断）。
 *
 * <p>確かめるのは「出ること」だけではない。申請の無い貨物に節を出さないこと、
 * <strong>承認待ちと決定済みを見分けられること</strong>まで見る。
 * すべてを「承認待ち」と出す実装や、常に節を出す実装で緑にしない。
 */
@AutoConfigureMockMvc
@DisplayName("予約詳細の訂正・取り消し申請の表示（C8）")
class CorrectionRequestsOnDetailTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 引取まで済んだ貨物（配送完了）を用意する。 */
    private UUID 引取済みの貨物(String trackingNumber) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("訂正表示テスト商事")
                .status("DELIVERED", "ROUTED")
                .trackingNumber(trackingNumber)
                .insert();
        return cargo.bookingId();
    }

    /** 引取を記録し、その荷役 ID を返す。 */
    private long 引取を記録する(UUID bookingId) {
        // **引取には荷受人確認が要る**（V14 の chk_handling_claim_confirmation）。
        // 制約を外して通すのではなく、業務どおりの行を作る
        jdbcTemplate.update("""
                INSERT INTO handling_activity (
                    booking_id, event_type, event_completion_time,
                    location_unlocode, operator_name, version,
                    claim_confirmation_method, claim_confirmation_code,
                    claim_consignee_name)
                VALUES (?, 'CLAIM', TIMESTAMP WITH TIME ZONE '2026-04-20 09:00:00+09',
                        'USLAX', '荷役太郎', 0, 'CODE', 'CLM-20260420-0001', '受取次郎')
                """, bookingId);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM handling_activity WHERE booking_id = ?",
                Long.class, bookingId);
    }

    private void 訂正を申請する(
            long handlingActivityId, String type, String reason, String status) {
        jdbcTemplate.update("""
                INSERT INTO handling_correction (
                    handling_activity_id, request_type, reason,
                    requested_by, requested_at, status,
                    decided_by, decided_at, decision_reason, version)
                VALUES (?, ?, ?, '荷役太郎',
                        TIMESTAMP WITH TIME ZONE '2026-04-21 09:00:00+09', ?,
                        ?, ?, ?, 0)
                """, handlingActivityId, type, reason, status,
                "PENDING".equals(status) ? null : "追跡花子",
                "PENDING".equals(status)
                        ? null : java.sql.Timestamp.valueOf("2026-04-22 09:00:00"),
                "REJECTED".equals(status) ? "貨物は確かに引き渡されている" : null);
    }

    private String 予約詳細(UUID bookingId) throws Exception {
        return mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * <strong>営業担当者が「取り消しが申請されている」ことを読める。</strong>
     *
     * <p>荷主から「届いていない」と電話を受けたとき、配送完了としか答えられないと
     * 話が進まない。取り消しの申請が出ていることが分かれば、
     * <strong>「いま確認中です」と答えられる</strong>。
     */
    @Test
    void 営業担当者が申請中の取り消しを読める() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260420-8801");
        long handlingId = 引取を記録する(bookingId);
        訂正を申請する(handlingId, "CANCEL", "別の貨物と取り違えて登録した", "PENDING");

        assertThat(予約詳細(bookingId))
                .contains("引取の訂正・取り消し")
                .contains("取り消し")
                .contains("承認待ち")
                .contains("別の貨物と取り違えて登録した")
                .contains("2026-04-21");
    }

    /**
     * <strong>承認待ちと決定済みを見分けられる。</strong>
     *
     * <p>すべてを「承認待ち」と出す実装で緑にしない。却下されたのに承認待ちと
     * 読めると、<strong>営業担当者は荷主に誤った見通しを伝える</strong>。
     * 却下の理由まで読めて初めて次の手が打てる。
     */
    @Test
    void 承認待ちと決定済みを見分けられる() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260420-8802");
        long handlingId = 引取を記録する(bookingId);
        訂正を申請する(handlingId, "CANCEL", "取り違えの疑い", "REJECTED");
        訂正を申請する(handlingId, "CORRECT", "作業日時が 1 日ずれている", "PENDING");

        String html = 予約詳細(bookingId);
        assertThat(html).contains("却下").contains("貨物は確かに引き渡されている");
        assertThat(html).contains("承認待ち");
    }

    /**
     * <strong>申請の無い貨物には節を出さない。</strong>
     *
     * <p>これが無いと、常に節を出す実装でも上の 2 件が緑になる。
     * 何も申請されていない貨物に見出しが出続けると、
     * <strong>見出しそのものが読み飛ばされる</strong>。
     */
    @Test
    void 申請の無い貨物には節を出さない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260420-8803");
        引取を記録する(bookingId);

        assertThat(予約詳細(bookingId)).doesNotContain("引取の訂正・取り消し");
    }

    /**
     * <strong>荷役の記録が無い予約でも詳細が開ける。</strong>
     *
     * <p>引取前の予約は日常的にある。ACL が空を返さずに落ちると、
     * <strong>予約詳細を開いただけで 500 になる</strong>。
     */
    @Test
    void 荷役の記録が無い予約でも詳細が開ける() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260420-8804");

        assertThat(予約詳細(bookingId)).contains("予約詳細");
    }

    /**
     * <strong>読み取り専用である。</strong>
     *
     * <p>承認・却下は追跡管理者の画面（{@code /handling/corrections}）の仕事である。
     * 予約詳細に操作を置くと、権限の分かれ目が画面ごとにばらける。
     */
    @Test
    void 予約詳細から申請を承認できない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260420-8805");
        long handlingId = 引取を記録する(bookingId);
        訂正を申請する(handlingId, "CANCEL", "取り違えの疑い", "PENDING");

        assertThat(予約詳細(bookingId))
                .doesNotContain("/approve")
                .doesNotContain("/reject");
    }
}
