package com.example.cargotracker.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 引取記録を訂正・取り消しする（US36）。
 *
 * <p>引取は輸送の終点であり、<strong>誤登録をそのままにすると貨物が届いていないのに
 * 配送完了として扱われる</strong>。IT6 のレビュー H11 が US 化を求めてから
 * 3 イテレーション繰り越した。
 *
 * <p>受入基準ごとに、その道を実行したテストを名指しする（IT11 のふりかえり T6）。
 *
 * <table>
 *   <caption>受入基準とテストの対応</caption>
 *   <tr><td>一覧から訂正・取り消しを申請できる</td>
 *       <td>取り消し: {@link #荷役作業員が理由を添えて取り消しを申請できる()}／
 *           訂正: {@link #訂正が承認されると作業日時とメモが直る()}
 *           （<strong>申請できるだけでなく、承認で中身が直るところまで</strong>）</td></tr>
 *   <tr><td>取り消しの承認で貨物状態が引取前に戻る</td>
 *       <td>{@link #承認すると貨物状態が引取前に戻る()}</td></tr>
 *   <tr><td>履歴が残り、元の記録は消えない</td>
 *       <td>{@link #元の記録は消えず取り消しの履歴が残る()}</td></tr>
 *   <tr><td>承認なしには状態が戻らない</td>
 *       <td>{@link #承認しなければ状態は戻らない()} /
 *           {@link #申請した本人は承認できない()}</td></tr>
 *   <tr><td>精算済みには申請できない</td>
 *       <td>{@link #精算済みの予約は申請できない()}</td></tr>
 * </table>
 */
@AutoConfigureMockMvc
@DisplayName("引取記録を訂正・取り消しする（US36）")
class ClaimCorrectionScenarioTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** **業務の暦を使う。** JVM 既定のゾーンで解釈すると CI（UTC）でだけずれる。 */
    @Autowired
    private java.time.Clock clock;

    /** 引取まで済んだ貨物を用意する（配送完了・引取完了）。 */
    private UUID 引取済みの貨物(String trackingNumber) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '訂正テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq),
                "correction-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number, consignee_name)
                VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'JPTYO', CURRENT_DATE + 60,
                        'IN_TRANSIT', 'ROUTED', ?, '受取花子')
                """, bookingId, shipperId, trackingNumber);
        Long cargoId = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0055', 'JPOSA', 'JPTYO',
                        TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-19 09:00:00+09', 1)
                """, cargoId);
        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'UNLOADED', 0)
                """, trackingNumber, bookingId);

        // **本番の経路で引取を登録する。** 直接 INSERT すると、
        // 予約・追跡の状態が実際の運用と違う形になる
        try {
            mockMvc.perform(post("/handling")
                    .param("trackingNumber", trackingNumber)
                    .param("type", "CLAIM")
                    .param("completionTime", "2026-04-21T10:00")
                    .param("locationUnlocode", "JPTYO")
                    .param("confirmationCode", "123456")
                    .param("consigneeName", "受取花子")
                    .param("operatorName", "港湾太郎")
                    .with(user("handler1").roles("HANDLER")).with(csrf()));
        } catch (Exception e) {
            throw new IllegalStateException("引取の登録に失敗しました", e);
        }
        return bookingId;
    }

    private long 荷役の識別子(String trackingNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM handling_activity WHERE tracking_number = ? "
                        + "AND event_type = 'CLAIM'", Long.class, trackingNumber);
    }

    private ResultActions 取り消しを申請する(long handlingId, String requester, String reason)
            throws Exception {
        return mockMvc.perform(post("/handling/corrections")
                .param("handlingId", String.valueOf(handlingId))
                .param("type", "CANCEL")
                .param("reason", reason)
                .with(user(requester).roles("HANDLER")).with(csrf()));
    }

    private long 申請の識別子(long handlingId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM handling_correction WHERE handling_activity_id = ?",
                Long.class, handlingId);
    }

    private ResultActions 承認する(long requestId, String approver) throws Exception {
        return mockMvc.perform(post("/handling/corrections/{id}/approval", requestId)
                .with(user(approver).roles("TRACKER")).with(csrf()));
    }

    private String 予約状態(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT booking_status FROM cargo WHERE booking_id = ?",
                String.class, bookingId);
    }

    private String 輸送状態(String trackingNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT transport_status FROM tracking_activity WHERE tracking_number = ?",
                String.class, trackingNumber);
    }

    /** 受入基準: 登録済みの引取記録を選び、訂正または取り消しを申請できる。 */
    @Test
    void 荷役作業員が理由を添えて取り消しを申請できる() throws Exception {
        引取済みの貨物("TRK-20260421-6301");
        long handlingId = 荷役の識別子("TRK-20260421-6301");

        取り消しを申請する(handlingId, "handler1", "別の貨物と取り違えて登録した")
                .andExpect(status().is3xxRedirection());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM handling_correction WHERE handling_activity_id = ?",
                String.class, handlingId);
        assertThat(status).isEqualTo("PENDING");
    }

    /**
     * <strong>理由の無い申請は受け付けない。</strong>
     *
     * <p>後から見ると「なぜ配送完了が取り消されたのか」が誰にも分からない。
     */
    @Test
    void 理由の無い申請は受け付けない() throws Exception {
        引取済みの貨物("TRK-20260421-6302");
        long handlingId = 荷役の識別子("TRK-20260421-6302");

        取り消しを申請する(handlingId, "handler1", "  ");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handling_correction WHERE handling_activity_id = ?",
                Integer.class, handlingId);
        assertThat(count).isZero();
    }

    /** 受入基準: 取り消しが承認されると、貨物状態が引取前の状態に戻る。 */
    @Test
    void 承認すると貨物状態が引取前に戻る() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260421-6303");
        long handlingId = 荷役の識別子("TRK-20260421-6303");
        assertThat(予約状態(bookingId)).isEqualTo("DELIVERED");
        assertThat(輸送状態("TRK-20260421-6303")).isEqualTo("CLAIMED");

        取り消しを申請する(handlingId, "handler1", "別の貨物と取り違えて登録した");
        承認する(申請の識別子(handlingId), "tracker1");

        assertThat(予約状態(bookingId))
                .as("届いていない貨物を配送完了のままにしない").isEqualTo("IN_TRANSIT");
        assertThat(輸送状態("TRK-20260421-6303"))
                .as("引取の直前の状態に戻る").isEqualTo("UNLOADED");
    }

    /**
     * 受入基準: 追跡管理者の承認なしには状態が戻らない。
     *
     * <p><strong>申請しただけでは何も動かない。</strong> 申請の時点で戻ると、
     * 承認という段階が意味を持たない。
     */
    @Test
    void 承認しなければ状態は戻らない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260421-6304");
        long handlingId = 荷役の識別子("TRK-20260421-6304");

        取り消しを申請する(handlingId, "handler1", "取り違えた");

        assertThat(予約状態(bookingId)).isEqualTo("DELIVERED");
        assertThat(輸送状態("TRK-20260421-6304")).isEqualTo("CLAIMED");
    }

    /**
     * <strong>申請した本人は承認できない。</strong>
     *
     * <p>一人で申請と承認ができるなら、承認という段階は形だけになる。
     * <strong>ボタンを隠すだけでは守りにならない</strong> — URL を叩いても
     * 状態が戻らないことを見る。
     */
    @Test
    void 申請した本人は承認できない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260421-6305");
        long handlingId = 荷役の識別子("TRK-20260421-6305");
        取り消しを申請する(handlingId, "handler1", "取り違えた");

        // 荷役作業員が追跡管理者のロールも持つ場合を踏む（同一人物）
        mockMvc.perform(post("/handling/corrections/{id}/approval", 申請の識別子(handlingId))
                .with(user("handler1").roles("TRACKER")).with(csrf()));

        assertThat(予約状態(bookingId))
                .as("申請者本人の承認で状態が戻ってはならない").isEqualTo("DELIVERED");
    }

    /** 受入基準: 訂正・取り消しの履歴が残り、<strong>元の記録は消えない</strong>。 */
    @Test
    void 元の記録は消えず取り消しの履歴が残る() throws Exception {
        引取済みの貨物("TRK-20260421-6306");
        long handlingId = 荷役の識別子("TRK-20260421-6306");
        取り消しを申請する(handlingId, "handler1", "取り違えた");
        承認する(申請の識別子(handlingId), "tracker1");

        // **荷役の行は残る。** 誰がいつ登録したかが消えると、事故時に経緯を追えない
        Integer activities = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handling_activity WHERE id = ?", Integer.class, handlingId);
        assertThat(activities).isEqualTo(1);

        var row = jdbcTemplate.queryForMap(
                "SELECT cancelled_at, cancelled_by FROM handling_activity WHERE id = ?",
                handlingId);
        assertThat(row.get("cancelled_at")).isNotNull();
        assertThat(row.get("cancelled_by")).isEqualTo("tracker1");

        var request = jdbcTemplate.queryForMap(
                "SELECT status, decided_by, decided_at, reason FROM handling_correction "
                        + "WHERE handling_activity_id = ?", handlingId);
        assertThat(request.get("status")).isEqualTo("APPROVED");
        assertThat(request.get("decided_by")).isEqualTo("tracker1");
        assertThat(request.get("decided_at")).isNotNull();
        assertThat(request.get("reason")).isEqualTo("取り違えた");
    }

    /**
     * 受入基準: <strong>訂正が承認されると記録の中身が直る</strong>。
     *
     * <p><strong>「申請できる」だけでは受入基準を満たさない。</strong> 承認しても
     * 何も変わらないなら、現場は直ったと思い込んだまま誤った記録を残す。
     * マニュアルは「訂正は記録の中身を直します」と書いており、
     * <strong>宣言が実装より多くを主張してはならない</strong>。
     */
    @Test
    void 訂正が承認されると作業日時とメモが直る() throws Exception {
        引取済みの貨物("TRK-20260421-6309");
        long handlingId = 荷役の識別子("TRK-20260421-6309");

        mockMvc.perform(post("/handling/corrections")
                .param("handlingId", String.valueOf(handlingId))
                .param("type", "CORRECT")
                .param("reason", "作業時刻を誤って登録した")
                .param("correctedCompletionTime", "2026-04-21T08:30")
                .param("correctedNote", "代理受領のため")
                .with(user("handler1").roles("HANDLER")).with(csrf()));
        承認する(申請の識別子(handlingId), "tracker1");

        var row = jdbcTemplate.queryForMap(
                "SELECT event_completion_time, note FROM handling_activity WHERE id = ?",
                handlingId);
        assertThat(row.get("note")).isEqualTo("代理受領のため");
        // **文字列で比べない。** timestamptz の表記は JVM のタイムゾーンで変わり、
        // CI（UTC）でだけ落ちる。**業務のゾーンで解釈した時刻**と突き合わせる
        java.time.Instant expected = java.time.LocalDateTime.of(2026, 4, 21, 8, 30)
                .atZone(clock.getZone()).toInstant();
        assertThat(((java.sql.Timestamp) row.get("event_completion_time")).toInstant())
                .as("作業日時が訂正されている").isEqualTo(expected);
    }

    /**
     * <strong>空欄にした項目は変えない</strong>（US36）。
     *
     * <p>作業日時だけを直す申請で、<strong>メモまで空になってはならない</strong>。
     * 現場が書いた「代理受領のため」が消えると、誰に渡したかの経緯が失われる。
     */
    @Test
    void 訂正で空欄にした項目は変わらない() throws Exception {
        引取済みの貨物("TRK-20260421-6310");
        long handlingId = 荷役の識別子("TRK-20260421-6310");
        jdbcTemplate.update(
                "UPDATE handling_activity SET note = ? WHERE id = ?", "元のメモ", handlingId);

        mockMvc.perform(post("/handling/corrections")
                .param("handlingId", String.valueOf(handlingId))
                .param("type", "CORRECT")
                .param("reason", "作業時刻だけを直す")
                .param("correctedCompletionTime", "2026-04-21T07:15")
                .with(user("handler1").roles("HANDLER")).with(csrf()));
        承認する(申請の識別子(handlingId), "tracker1");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT note FROM handling_activity WHERE id = ?", String.class, handlingId))
                .as("入力しなかった項目は変わらない").isEqualTo("元のメモ");
    }

    /**
     * <strong>中身の無い訂正は申請できない</strong>（US36）。
     *
     * <p>承認しても何も起きない申請が待ち行列に並ぶと、
     * <strong>承認する側は「何を承認したのか」が分からない</strong>。
     */
    @Test
    void 中身の無い訂正は申請できない() throws Exception {
        引取済みの貨物("TRK-20260421-6311");
        long handlingId = 荷役の識別子("TRK-20260421-6311");

        mockMvc.perform(post("/handling/corrections")
                .param("handlingId", String.valueOf(handlingId))
                .param("type", "CORRECT")
                .param("reason", "直したい")
                .with(user("handler1").roles("HANDLER")).with(csrf()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handling_correction WHERE handling_activity_id = ?",
                Integer.class, handlingId)).isZero();
    }

    /**
     * <strong>訂正の承認では貨物状態を戻さない</strong>（T1 の数え上げで見つかった主張）。
     *
     * <p>取り消しは輸送の状態を引取前に戻すが、訂正は記録の中身だけを直す。
     * <strong>同じ「直す」でも承認したときに起きることが違う。</strong>
     * 種別を見ずに戻す実装だと、<strong>作業時刻を直しただけで配送完了が消える</strong>。
     */
    @Test
    void 訂正の承認では貨物状態を戻さない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260421-6308");
        long handlingId = 荷役の識別子("TRK-20260421-6308");

        mockMvc.perform(post("/handling/corrections")
                .param("handlingId", String.valueOf(handlingId))
                .param("type", "CORRECT")
                .param("reason", "作業時刻を誤って登録した")
                .param("correctedNote", "作業時刻を訂正した")
                .with(user("handler1").roles("HANDLER")).with(csrf()));
        承認する(申請の識別子(handlingId), "tracker1");

        assertThat(予約状態(bookingId))
                .as("訂正で配送完了が消えてはならない").isEqualTo("DELIVERED");
        assertThat(輸送状態("TRK-20260421-6308")).isEqualTo("CLAIMED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cancelled_at FROM handling_activity WHERE id = ?",
                java.sql.Timestamp.class, handlingId))
                .as("訂正では荷役を取り消さない").isNull();
    }

    /**
     * 受入基準: 精算済み（{@code SETTLED}）の予約に対しては訂正・取り消しできない。
     *
     * <p>精算は請求と入金を伴い、取り消しは<strong>返金の業務</strong>になる。
     * ここで通すと、業務として実行できない承認待ちが待ち行列に残り続ける。
     */
    @Test
    void 精算済みの予約は申請できない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260421-6307");
        long handlingId = 荷役の識別子("TRK-20260421-6307");
        jdbcTemplate.update(
                "UPDATE cargo SET booking_status = 'SETTLED' WHERE booking_id = ?", bookingId);

        取り消しを申請する(handlingId, "handler1", "取り違えた");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handling_correction WHERE handling_activity_id = ?",
                Integer.class, handlingId);
        assertThat(count).as("実行できない申請を待ち行列に残さない").isZero();
    }
}
