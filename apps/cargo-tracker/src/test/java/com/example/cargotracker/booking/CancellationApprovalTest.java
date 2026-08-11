package com.example.cargotracker.booking;

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
 * 輸送中の予約キャンセルを画面から通す（US30）。
 *
 * <p><strong>集約の単体テストは画面での見え方を判別しない。</strong>
 * 遷移表を分けただけでは、営業担当者の画面から [キャンセル] が消えたことも、
 * 追跡管理者が承認画面を開けることも確かめられない。
 *
 * <p><strong>認可の規則の順序はここでしか捕まらない</strong>（X2）。
 * {@code /bookings/cancellations/{id}} は 2 セグメントであり
 * {@code GET /bookings/*} に一致しないため、規則を後ろに書くと 403 になる。
 */
@AutoConfigureMockMvc
@DisplayName("輸送中の予約キャンセルの承認（US30）")
class CancellationApprovalTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.example.cargotracker.booking.application.internal.queryservices
            .CancellationQueryService queryService;

    @Autowired
    private com.example.cargotracker.support.QueryCounter queryCounter;

    /**
     * <strong>輸送中の予約に [キャンセル] を出さない</strong>（受入基準 2）。
     *
     * <p>いまは押せてしまう。<strong>押した瞬間に、船の上にある貨物の
     * 行き先が消える。</strong>
     */
    @Test
    void 輸送中の予約には即時キャンセルのボタンが出ず申請だけが出る() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260810-9001");

        String html = 予約詳細(bookingId, "sales1", "SALES");

        assertThat(html)
                .as("**押せない操作を見せない**")
                .doesNotContain("/cancel\"");
        assertThat(html).contains("キャンセルを申請");
    }

    /** <strong>理由を入れないと申請できない</strong>（デモ項目 3）。 */
    @Test
    void 理由の無い申請は業務の言葉で拒む() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260810-9002");

        mockMvc.perform(post("/bookings/{id}/cancellation", bookingId)
                        .param("reason", "  ")
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(承認待ち一覧("tracker1"))
                .as("申請が作られていない")
                .doesNotContain("TRK-20260810-9002");
    }

    /**
     * <strong>申請 → 承認でキャンセルが確定する</strong>（受入基準 3・4）。
     *
     * <p><strong>申請の時点では状態が動かない。</strong> 承認されるまで
     * キャンセルは確定しない — 貨物は船の上にあり、降ろす場所が決まるまで
     * 運び続けるほうが安全である。
     */
    @Test
    void 申請してから承認するとキャンセルが確定する() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260810-9003");

        申請する(bookingId, "荷主都合");

        assertThat(予約状態(bookingId))
                .as("**申請しただけでは輸送は止まらない**")
                .isEqualTo("IN_TRANSIT");

        long requestId = 申請id(bookingId);
        // **追跡管理者が承認画面を開ける**（X2。規則の順序を後ろに書くと 403）
        String detail = mockMvc.perform(get("/bookings/cancellations/{id}", requestId)
                        .with(user("tracker1").roles("TRACKER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(detail)
                .as("**陸揚げ地は候補から選ぶ**（自由入力ではない）")
                .contains("JPOSA");

        mockMvc.perform(post("/bookings/cancellations/{id}/approval", requestId)
                        .param("discharge", "JPOSA")
                        .with(user("tracker1").roles("TRACKER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(予約状態(bookingId)).isEqualTo("CANCELLED");
    }

    /**
     * <strong>却下すると輸送中のまま維持される</strong>（受入基準 5）。
     *
     * <p><strong>却下の理由が予約詳細から読める。</strong> 却下したことも経緯である。
     */
    @Test
    void 却下すると輸送中のまま維持され理由が予約詳細から読める() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260810-9004");
        申請する(bookingId, "荷主都合");
        long requestId = 申請id(bookingId);

        mockMvc.perform(post("/bookings/cancellations/{id}/rejection", requestId)
                        .param("reason", "代替の販売先が見つかったため輸送を続ける")
                        .with(user("tracker1").roles("TRACKER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(予約状態(bookingId)).isEqualTo("IN_TRANSIT");
        assertThat(予約詳細(bookingId, "sales1", "SALES"))
                .as("**却下の理由が読める**。申請者は次に何をすればよいか分かる")
                .contains("代替の販売先が見つかったため輸送を続ける")
                .contains("予約キャンセルの申請");
    }

    /**
     * <strong>追跡管理者以外は承認できない</strong>（デモ項目 12。X2）。
     *
     * <p>参照は営業担当者にも開く — 自分が出した申請の行方を追えないと、
     * 荷主に答えられない。<strong>決めるのは追跡管理者だけである。</strong>
     */
    @Test
    void 承認できるのは追跡管理者だけである() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260810-9005");
        申請する(bookingId, "荷主都合");
        long requestId = 申請id(bookingId);

        mockMvc.perform(get("/bookings/cancellations/{id}", requestId)
                        .with(user("sales1").roles("SALES")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/bookings/cancellations/{id}/approval", requestId)
                        .param("discharge", "JPOSA")
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/bookings/cancellations")
                        .with(user("handler1").roles("HANDLER")))
                .andExpect(status().isForbidden());

        assertThat(予約状態(bookingId)).isEqualTo("IN_TRANSIT");
    }

    /**
     * <strong>候補にない陸揚げ地では承認できない</strong>（受入基準 3）。
     *
     * <p>船が寄らない港を指定すると、降ろせない場所で降ろす手配をすることになる。
     */
    @Test
    void 候補にない陸揚げ地では承認できない() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260810-9006");
        申請する(bookingId, "荷主都合");
        long requestId = 申請id(bookingId);

        mockMvc.perform(post("/bookings/cancellations/{id}/approval", requestId)
                        .param("discharge", "CNSHA")
                        .with(user("tracker1").roles("TRACKER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(予約状態(bookingId))
                .as("**降ろせない港で降ろす手配をしない**")
                .isEqualTo("IN_TRANSIT");
    }

    /**
     * <strong>決着していない申請は 1 件までである</strong>（US30）。
     *
     * <p>2 件並ぶと、追跡管理者は同じ貨物について 2 度承認でき、
     * <strong>陸揚げ地が 2 か所決まる</strong>。
     */
    @Test
    void 二重の申請は業務の言葉で拒む() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260810-9007");
        申請する(bookingId, "荷主都合");

        mockMvc.perform(post("/bookings/{id}/cancellation", bookingId)
                        .param("reason", "もう一度")
                        .with(user("sales2").roles("SALES")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_cancellation WHERE booking_id = ?",
                Integer.class, bookingId))
                .as("**制約に頼ると画面には 500 が出る**")
                .isEqualTo(1);
    }

    /**
     * <strong>引き渡し済みの貨物は申請できない</strong>（デモ項目 13）。
     *
     * <p>引き渡し済み貨物の取り消しは返送であり、別の業務である。
     */
    @Test
    void 引き渡し済みの貨物はキャンセルを申請できない() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260810-9008");
        jdbcTemplate.update(
                "UPDATE cargo SET booking_status = 'DELIVERED' WHERE booking_id = ?",
                bookingId);

        String html = 予約詳細(bookingId, "sales1", "SALES");

        assertThat(html)
                .as("**押せない操作を見せない**")
                .doesNotContain("キャンセルを申請")
                .doesNotContain("/cancel\"");
    }

    /**
     * <strong>ダッシュボードのカードから承認待ち一覧へ行ける</strong>（デモ項目 4・5）。
     *
     * <p><strong>件数を出すだけでは仕事は進まない</strong>（IT9 のふりかえり T2）。
     */
    @Test
    void 追跡管理者はカードとナビから承認待ち一覧へ行ける() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260810-9009");
        申請する(bookingId, "荷主都合");

        String dashboard = mockMvc.perform(get("/")
                        .with(user("tracker1").roles("TRACKER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(dashboard)
                .contains("キャンセル承認待ち")
                .as("**行き先が数えた対象と一致する**（C33 の型）")
                .contains("/bookings/cancellations");
    }

    /**
     * <strong>承認するとキャンセル料の請求書ができる</strong>（デモ項目 10。X1）。
     *
     * <p><strong>同期のポートにしない</strong>（ADR-021）。承認画面の前にいる
     * 追跡管理者は、キャンセル料について何もできない。イベントで Billing へ渡し、
     * <strong>金額は Billing が決める</strong>（金額の正典は Billing にある）。
     *
     * <p><strong>輸送料金の請求書とは別の種別として並ぶ。</strong>
     * V1 の「予約ごとに 1 枚」では 2 枚目が入らなかった。
     */
    @Test
    void 承認するとキャンセル料の請求書ができる() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260810-9020");
        申請する(bookingId, "荷主都合");
        long requestId = 申請id(bookingId);

        mockMvc.perform(post("/bookings/cancellations/{id}/approval", requestId)
                        .param("discharge", "JPOSA")
                        .with(user("tracker1").roles("TRACKER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM invoice
                 WHERE booking_id = ? AND invoice_type = 'CANCELLATION'
                """, Integer.class, bookingId))
                .as("**キャンセル料が請求に引き渡される**")
                .isEqualTo(1);

        // 輸送中の料率は 50%。基本料金の半額が請求の基準額になる
        assertThat(jdbcTemplate.queryForObject("""
                SELECT charge_status FROM invoice
                 WHERE booking_id = ? AND invoice_type = 'CANCELLATION'
                """, String.class, bookingId))
                .as("**下書きで作る。** 承認と同時に確定すると金額を目で見る場が無くなる")
                .isEqualTo("DRAFT");

        // **何のお金かが画面から読める**（ADR-020。IT15 レビュー M8 / IT16 の C6）。
        // 輸送料金と並んだときに区別がつかないと、経理担当者は
        // どちらを督促しているのか分からなくなる
        String invoiceList = mockMvc.perform(get("/billing/invoices")
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(invoiceList)
                .as("**種別が一覧から読めること**")
                .contains("キャンセル料");
    }

    /**
     * <strong>承認・却下が荷主への通知として残る</strong>（受入基準 4・5）。
     *
     * <p>ADR-006 により外部へは送らない。残すのは<strong>いつ・誰に・何を伝えたか</strong>
     * であり、荷主から「聞いていない」と言われたときに答えるための記録である。
     *
     * <p><strong>申請の時点では通知を残さない。</strong> 荷主にはまだ何も確定していない。
     */
    @Test
    void 承認と却下は荷主への通知として残る() throws Exception {
        UUID approved = 輸送中の貨物("TRK-20260810-9021");
        申請する(approved, "荷主都合");

        assertThat(通知種別(approved))
                .as("**申請の時点では荷主に何も確定していない**")
                .isEmpty();

        mockMvc.perform(post("/bookings/cancellations/{id}/approval", 申請id(approved))
                        .param("discharge", "JPOSA")
                        .with(user("tracker1").roles("TRACKER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(通知種別(approved)).containsExactly("CANCELLATION_APPROVED");
        assertThat(通知内容(approved))
                .as("**陸揚げ地を文面に残す。**「承認しました」だけでは荷主は動けない")
                .contains("JPOSA");

        UUID rejected = 輸送中の貨物("TRK-20260810-9022");
        申請する(rejected, "荷主都合");
        mockMvc.perform(post("/bookings/cancellations/{id}/rejection", 申請id(rejected))
                        .param("reason", "代替の販売先が見つかったため")
                        .with(user("tracker1").roles("TRACKER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(通知種別(rejected)).containsExactly("CANCELLATION_REJECTED");
        assertThat(通知内容(rejected))
                .as("**理由を文面に残す。** 荷主は次に何をすればよいか分かる")
                .contains("代替の販売先が見つかったため");
    }

    private java.util.List<String> 通知種別(UUID bookingId) {
        return jdbcTemplate.queryForList(
                "SELECT notification_type FROM booking_notification WHERE booking_id = ?",
                String.class, bookingId);
    }

    private String 通知内容(UUID bookingId) {
        return String.join(" / ", jdbcTemplate.queryForList(
                "SELECT content FROM booking_notification WHERE booking_id = ?",
                String.class, bookingId));
    }

    /**
     * <strong>承認待ち一覧が件数に比例して問い合わせを増やさない</strong>（C4 と同じ型）。
     *
     * <p>1 行ずつ予約を引き直すと、<strong>待ち行列が伸びるほど遅くなる</strong> —
     * いちばん混んでいるときに、いちばん遅い。
     *
     * <p><strong>件数を変えて増え方を見る。</strong> 絶対値を固定すると、
     * 実装を少し変えるたびに落ちて意味を失う。
     */
    @Test
    void 承認待ち一覧が件数に比例して問い合わせを増やさない() throws Exception {
        申請する(輸送中の貨物("TRK-20260810-9030"), "荷主都合");
        int forOne = 問い合わせ回数(() -> queryService.findPending());

        for (int i = 1; i <= 4; i++) {
            申請する(輸送中の貨物("TRK-20260810-904%d".formatted(i)), "荷主都合");
        }
        int forFive = 問い合わせ回数(() -> queryService.findPending());

        assertThat(forFive)
                .as("1 件のとき %d 回、5 件のとき %d 回。**1 行ごとに問い合わせている**",
                        forOne, forFive)
                .isEqualTo(forOne);
    }

    private int 問い合わせ回数(Runnable action) {
        queryCounter.reset();
        action.run();
        return queryCounter.count();
    }

    /** 承認待ち一覧を開く。 */
    private String 承認待ち一覧(String username) throws Exception {
        return mockMvc.perform(get("/bookings/cancellations")
                        .with(user(username).roles("TRACKER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String 予約詳細(UUID bookingId, String username, String role) throws Exception {
        return mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user(username).roles(role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void 申請する(UUID bookingId, String reason) throws Exception {
        mockMvc.perform(post("/bookings/{id}/cancellation", bookingId)
                        .param("reason", reason)
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private long 申請id(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM booking_cancellation WHERE booking_id = ?",
                Long.class, bookingId);
    }

    private String 予約状態(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT booking_status FROM cargo WHERE booking_id = ?",
                String.class, bookingId);
    }

    /** 輸送中の貨物を用意する（旅程と追跡の記録つき）。 */
    private UUID 輸送中の貨物(String trackingNumber) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("キャンセルテスト商事")
                .status("IN_TRANSIT", "ROUTED")
                .trackingNumber(trackingNumber)
                .insert();
        UUID bookingId = cargo.bookingId();
        long cargoId = cargo.cargoId();
        // **まだ着いていない揚地だけが候補になる**ため、未来の時刻にする
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0001', 'JPOSA', 'USLAX',
                        CURRENT_TIMESTAMP + INTERVAL '1 day',
                        CURRENT_TIMESTAMP + INTERVAL '20 days', 1)
                """, cargoId);

        // 現在地は大阪（最後の荷役の発生場所）
        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'ONBOARD_CARRIER', 0)
                """, trackingNumber, bookingId);
        Long trackingId = jdbcTemplate.queryForObject(
                "SELECT id FROM tracking_activity WHERE tracking_number = ?",
                Long.class, trackingNumber);
        jdbcTemplate.update("""
                INSERT INTO tracking_handling_event (
                    tracking_id, event_type, event_time, location_unlocode,
                    voyage_number, source)
                VALUES (?, 'LOAD', CURRENT_TIMESTAMP, 'JPOSA', 'V0001', 'HANDLING')
                """, trackingId);
        return bookingId;
    }
}
