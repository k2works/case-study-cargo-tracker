package com.example.cargotracker.handling;

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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 訂正・取り消しの承認ボタンを誰に見せるか（US36 / IT12 持ち越し C9）。
 *
 * <p><strong>押せない操作を見せない。</strong> 承認待ちの一覧は荷役作業員も開ける
 * （自分の申請の行方を読むため）。そこに承認・却下のボタンが出ていると、
 * 押した瞬間に 403 になる。
 *
 * <p>ロールだけでは足りない。<strong>小規模な拠点では追跡管理者が荷役も兼ねる。</strong>
 * 自分で申請して自分の画面で承認ボタンを見ることが日常的に起きる。
 * ドメインは本人の承認を拒む（US36 の受入基準）が、
 * <strong>ボタンが出ていれば押した瞬間にエラーになり、
 * なぜ押せないのかはどこにも書いていない</strong>。
 *
 * <p><strong>本クラスは {@code ClaimCorrectionScenarioTest} から切り出した。</strong>
 * 500 行の上限に当たったのは合図である。業務シナリオ（申請 → 承認 → 状態が戻る）と、
 * <strong>誰にボタンを見せるか</strong>は別の関心である。
 */
@AutoConfigureMockMvc
@DisplayName("訂正・取り消しの承認ボタンの出し分け（C9）")
class CorrectionApprovalVisibilityTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** **「今日」をアプリと同じ時計で決める。** JVM 既定だと CI（UTC）だけ落ちる。 */
    @Autowired
    private java.time.Clock clock;

    /**
     * <strong>引取を登録すると、いつ引取が済んだかが予約に残る</strong>（IT13 レビュー C1）。
     *
     * <p>請求対象一覧の引取日は、これが書かれていて初めて出る。
     * <strong>画面の列だけを足しても、書く側が無ければ一生「不明」である。</strong>
     *
     * <p><strong>登録日時ではなく作業日時を残す。</strong> 現場は後から登録することがあり、
     * 登録日で締めると月をまたいだ引取が当月に混ざる。
     */
    @Test
    void 引取の登録で引取日時が予約に残る() {
        UUID bookingId = 引取済みの貨物("TRK-20260421-6320");

        java.time.Instant claimedAt = jdbcTemplate.queryForObject(
                "SELECT claimed_at FROM cargo WHERE booking_id = ?",
                java.time.Instant.class, bookingId);

        assertThat(claimedAt)
                .as("**荷役の作業日時がそのまま残る**（登録した日時ではない）")
                .isEqualTo(java.time.LocalDateTime.parse("2026-04-21T10:00")
                        .atZone(clock.getZone()).toInstant());
    }

    /**
     * <strong>押せない操作を見せない</strong>（US36）。
     *
     * <p>承認待ちの一覧は荷役作業員も開ける（自分の申請の行方を読むため）。
     * <strong>そこに承認・却下のボタンが出ていると、押した瞬間に 403 になる。</strong>
     * 申請した本人が「承認できるように見える」画面を見せられている。
     * 「共有した画面のリンクもロールで出し分ける」の再発である。
     *
     * <p><strong>申請が 1 件ある状態で見る。</strong> 空の一覧では、
     * ボタンを出す実装でも「無いこと」の検査が通ってしまう。
     */
    @Test
    void 承認のボタンは追跡管理者にだけ出る() throws Exception {
        引取済みの貨物("TRK-20260421-6312");
        long handlingId = 荷役の識別子("TRK-20260421-6312");
        取り消しを申請する(handlingId, "handler1", "取り違えた");

        String forTracker = mockMvc.perform(get("/handling/corrections")
                        .with(user("tracker1").roles("TRACKER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(forTracker)
                .as("承認する人にはボタンが出る（開けたことを先に見る）")
                .contains("/approval");

        String forHandler = mockMvc.perform(get("/handling/corrections")
                        .with(user("handler1").roles("HANDLER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(forHandler)
                .as("押すと 403 になる操作を見せてはならない")
                .doesNotContain("/approval");
        assertThat(forHandler)
                .as("申請の行方は読める（一覧そのものは開ける）")
                .contains("取り違えた");
    }

    /**
     * <strong>自分が申請したものには承認ボタンを出さない</strong>（IT12 持ち越し C9）。
     *
     * <p>ドメインは申請した本人の承認を拒む（US36 の受入基準）。しかし画面は
     * ロールでしか出し分けておらず、<strong>追跡管理者が自分で申請すると
     * 押せるボタンが出る</strong>。押した瞬間にエラーになり、
     * 「なぜ押せないのか」は画面のどこにも書いていない。
     *
     * <p>小規模な拠点では追跡管理者が荷役も兼ねる。<strong>兼務は例外ではなく日常である。</strong>
     *
     * <p><strong>他人の申請では出ることを対で見る。</strong> 一律に消す実装では、
     * 承認そのものができなくなる（「無いこと」だけを見ると通ってしまう）。
     */
    @Test
    void 自分が申請したものには承認ボタンを出さない() throws Exception {
        引取済みの貨物("TRK-20260421-6314");
        long handlingId = 荷役の識別子("TRK-20260421-6314");
        // **追跡管理者が自分で申請する。** 兼務の拠点では日常的に起きる
        取り消しを申請する(handlingId, "tracker1", "自分で取り違えた");

        // **一覧には他の申請も並ぶ。** ページ全体で「/approval が無い」を見ると、
        // 他人の申請のボタンを拾って落ちる。**自分の申請の行だけを取り出す**
        String selfRow = 申請の行(承認一覧("tracker1"), "自分で取り違えた");
        assertThat(selfRow)
                .as("押すとエラーになる操作を見せてはならない")
                .doesNotContain("/approval");
        assertThat(selfRow)
                .as("なぜ押せないのかを画面に書く。書かないと不具合に見える")
                .contains("あなたが申請したものです");

        String otherRow = 申請の行(承認一覧("tracker2"), "自分で取り違えた");
        assertThat(otherRow)
                .as("他人の申請では承認できる（一律に消す実装で緑にしない）")
                .contains("/approval");
    }

    private String 承認一覧(String viewer) throws Exception {
        return mockMvc.perform(get("/handling/corrections")
                        .with(user(viewer).roles("TRACKER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * 理由で申請の行を切り出す。
     *
     * <p><strong>ページ全体を見ると、他の申請のボタンを拾う。</strong>
     * 「無いこと」を見るアサートは、対象を絞らないと簡単に空振りする。
     */
    private String 申請の行(String html, String reason) {
        int at = html.indexOf(reason);
        assertThat(at).as("対象の申請が一覧に出ている").isGreaterThan(-1);
        int start = html.lastIndexOf("<tr", at);
        int end = html.indexOf("</tr>", at);
        return html.substring(start, end);
    }

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
}
