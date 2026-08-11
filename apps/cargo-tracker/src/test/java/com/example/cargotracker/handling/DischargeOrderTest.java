package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.handling.application.internal.queryservices.DischargeOrderView;
import com.example.cargotracker.handling.application.internal.queryservices.HandlingQueryService;
import com.example.cargotracker.support.CargoFixture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.List;
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

    @Autowired
    private HandlingQueryService queryService;

    @Autowired
    private com.example.cargotracker.support.QueryCounter queryCounter;

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
     */
    @Test
    void 荷役作業員は荷降し手配に到達できる() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7006");
        承認する(bookingId, "JPTYO");

        assertThat(荷役作業一覧("handler1", "HANDLER")).contains("TRK-20260811-7006");
    }

    /**
     * <strong>追跡管理者も荷降し手配を読める</strong>（IT17 の A1）。
     *
     * <p><strong>正典の食い違いを IT17 の開始準備で決着させた。</strong>
     * {@code ui_design.md}（`ROLE_HANDLER, ROLE_TRACKER`）と `SecurityConfig`
     * （`ROLE_HANDLER` のみ）が割れており、IT16 では<strong>認可を広げるのは
     * テストで既成事実にしてよい変更ではない</strong>として実装を動かさなかった。
     *
     * <p><strong>`ui_design.md` を正とした。</strong> 追跡管理者は訂正・取り消しの承認
     * （US36）とキャンセルの承認（US30）を行う立場であり、
     * {@code /handling/corrections} と {@code /handling/customs} には既に GET で入れる。
     *
     * <p><strong>荷降し手配は追跡管理者自身が承認した結果である</strong>（US30）。
     * 承認した手配が現場に届いたかを確かめられないのは、
     * <strong>「気づく手段は次の行動へ繋ぐ」の裏返し</strong>になる。
     */
    @Test
    void 追跡管理者も荷降し手配を読める() throws Exception {
        UUID bookingId = 輸送中の貨物("TRK-20260811-7008");
        承認する(bookingId, "JPTYO");

        assertThat(荷役作業一覧("tracker1", "TRACKER"))
                .as("**承認した手配が現場に届いたかを確かめられる**")
                .contains("TRK-20260811-7008")
                .contains("荷降し手配");
    }

    /**
     * <strong>追跡管理者がダッシュボードと navbar から荷役作業一覧へ到達できる</strong>
     * （IT17 の A1）。
     *
     * <p><strong>開いたのに入口が無い状態にしない。</strong> 認可だけ広げても、
     * URL を組み立てられる人しか行けない（IT16 の C5 で経理担当者に同じことが起きていた）。
     */
    @Test
    void 追跡管理者はダッシュボードと画面上部から荷役作業一覧へ行ける() throws Exception {
        String dashboard = mockMvc.perform(get("/").with(user("tracker1").roles("TRACKER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(dashboard)
                .as("**入口がある**（カードと navbar の両方）")
                .contains("荷役管理")
                .contains("/handling");
        assertThat(dashboard)
                .as("**読めることと操作できることを混ぜない**と画面でも伝える")
                .contains("記録は荷役作業員が行います");
    }

    /**
     * <strong>追跡管理者は荷役を登録できない</strong>（IT17 の A1）。
     *
     * <p><strong>読めることと操作できることを混ぜない。</strong> 荷役の登録・訂正の申請は
     * 現場の作業であり、追跡管理者が代行するものではない
     * （{@code /handling/customs} と同じ扱い）。
     *
     * <p><strong>画面にボタンを出さないことは認可ではない</strong>（IT11 の教訓）。
     * 見えないまま URL を叩けば実行できる状態にしない。
     */
    @Test
    void 追跡管理者は荷役を登録できない() throws Exception {
        mockMvc.perform(get("/handling/new").with(user("tracker1").roles("TRACKER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/handling")
                        .param("trackingNumber", "TRK-20260811-7009")
                        .param("type", "UNLOAD")
                        .with(user("tracker1").roles("TRACKER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * <strong>危険物の手配は現場が気づける</strong>（US05。レビュー M1）。
     *
     * <p>降ろす準備は貨物種別で変わる。荷役の履歴側には貨物種別が出ているのに、
     * <strong>先に読む手配側に出ていないと、申告を登録した意味が半分になる</strong>。
     *
     * <p><strong>一般貨物では出さない。</strong> すべての行にバッジが付くと、
     * 気をつけるべき行が埋もれる。
     */
    @Test
    void 危険物の手配には取り扱いのバッジが出る() throws Exception {
        承認する(輸送中の貨物("TRK-20260811-7301", "HAZARDOUS"), "JPTYO");
        承認する(輸送中の貨物("TRK-20260811-7302", "GENERAL"), "JPTYO");

        List<DischargeOrderView> orders = queryService.findPendingDischarges();

        assertThat(orders)
                .filteredOn(o -> "TRK-20260811-7301".equals(o.trackingNumber()))
                .singleElement()
                .satisfies(o -> {
                    assertThat(o.needsSpecialHandling()).isTrue();
                    assertThat(o.cargoTypeLabel()).isEqualTo("危険物");
                });
        assertThat(orders)
                .filteredOn(o -> "TRK-20260811-7302".equals(o.trackingNumber()))
                .singleElement()
                .satisfies(o -> assertThat(o.needsSpecialHandling())
                        .as("一般貨物にバッジを出さない（気をつけるべき行を埋もれさせない）")
                        .isFalse());

        assertThat(荷役作業一覧("handler1", "HANDLER")).contains("危険物");
    }

    /**
     * <strong>待たせている手配から捌く</strong>（並びは承認の古い順）。
     *
     * <p><strong>並び順は実装にあるだけでは守られない。</strong> {@code ORDER BY} を
     * 外しても、他のテストはすべて緑のままだった。
     * <strong>「出ること」と「読む順に出ること」は別である。</strong>
     *
     * <p><strong>登録順と承認順をわざと食い違わせる。</strong> 素直に順番に承認すると、
     * 行の物理的な並びが承認順と一致してしまい、<strong>{@code ORDER BY} を外しても
     * 同じ結果が出る</strong>（本テストの初版が実際にそうだった）。
     * <strong>並び替えだけが立っている状態</strong>を作って確かめる。
     */
    @Test
    void 手配は承認の古い順に並ぶ() throws Exception {
        UUID first = 輸送中の貨物("TRK-20260811-7101");
        UUID second = 輸送中の貨物("TRK-20260811-7102");
        UUID third = 輸送中の貨物("TRK-20260811-7103");
        承認する(first, "JPTYO");
        承認する(second, "JPTYO");
        承認する(third, "JPTYO");

        // 登録順とは逆に承認したことにする（**最後に登録した 7103 がいちばん古い承認**）
        承認日時をずらす(third, 3);
        承認日時をずらす(second, 2);
        承認日時をずらす(first, 1);

        List<DischargeOrderView> orders = queryService.findPendingDischarges();

        assertThat(orders)
                .extracting(DischargeOrderView::trackingNumber)
                .as("**登録順ではなく承認の古い順に並ぶこと**")
                .containsSubsequence(
                        "TRK-20260811-7103", "TRK-20260811-7102", "TRK-20260811-7101");
    }

    /** 承認日時を「n 時間前」にする（並び順を判別できる形を作るため）。 */
    private void 承認日時をずらす(UUID bookingId, int hoursAgo) {
        int updated = jdbcTemplate.update("""
                UPDATE booking_cancellation
                   SET decided_at = ?
                 WHERE booking_id = ? AND status = 'APPROVED'
                """,
                java.sql.Timestamp.from(
                        java.time.Instant.now().minusSeconds(3600L * hoursAgo)),
                bookingId);
        // **更新できていないテストは、並び順を確かめていない。**
        // 0 件のまま緑になると、検査そのものが空振りする
        assertThat(updated).as("承認日時をずらせていること").isEqualTo(1);
    }

    /**
     * <strong>手配が増えても問い合わせ回数は増えない</strong>（Try T3）。
     *
     * <p><strong>時間で測らない。</strong> 経過時間のアサートは、遅いマシンでは偽陽性、
     * 速いマシンでは N+1 を残したままでも緑になる。<strong>何回問い合わせたかを数える。</strong>
     *
     * <p><strong>件数を変えて増え方を見る。</strong> 1 件のときと 5 件のときで
     * 回数が変わらなければ、件数に比例していない。絶対値を固定すると、
     * 実装を少し変えるたびに落ちて意味を失う。
     *
     * <p>本テストは<strong>途中レビューの H1 として追加した</strong>。
     * T3 は「一覧を返すクエリサービスを書いたら<strong>その場で</strong>書く」と定めており、
     * <strong>本イテレーションの実装がその規律を破っていた</strong>（IT15 の P3 と同じ形）。
     */
    @Test
    void 手配が増えても問い合わせ回数は増えない() throws Exception {
        承認する(輸送中の貨物("TRK-20260811-7201"), "JPTYO");
        queryCounter.reset();
        int oneOrder = 手配を数える();

        for (int i = 2; i <= 5; i++) {
            承認する(輸送中の貨物("TRK-20260811-72%02d".formatted(i)), "JPTYO");
        }
        queryCounter.reset();
        int fiveOrders = 手配を数える();

        assertThat(fiveOrders)
                .as("""
                        手配の件数に比例して問い合わせが増えています。

                        **待ち行列が伸びるほど遅くなります** — いちばん混んでいるときに、
                        いちばん遅い（IT13 の C4 / IT15 の P3）。
                        まとめて引いてください。""")
                .isEqualTo(oneOrder);
    }

    private int 手配を数える() {
        queryService.findPendingDischarges();
        return queryCounter.count();
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
        return 輸送中の貨物(trackingNumber, "GENERAL");
    }

    private UUID 輸送中の貨物(String trackingNumber, String cargoType) {
        CargoFixture fixture = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("荷降し手配テスト商事")
                .route("JPOSA", "USLAX")
                .status("IN_TRANSIT", "ROUTED")
                .trackingNumber(trackingNumber);
        if ("HAZARDOUS".equals(cargoType)) {
            // **申告の無い危険物は集約が受け付けない**（US05）
            fixture.hazardous("UN1263", "3", "PAINT");
        } else {
            fixture.cargoType(cargoType);
        }
        CargoFixture.Inserted cargo = fixture.insert();

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
