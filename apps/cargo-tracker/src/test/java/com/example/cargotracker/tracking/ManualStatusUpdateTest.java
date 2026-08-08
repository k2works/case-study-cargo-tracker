package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * US17: 貨物状態を手動更新する。受入基準に 1:1 で対応させる。
 *
 * <p><strong>出港・入港は荷役作業員が登録しない。</strong> 船が出たことは荷役の記録に
 * 現れず、手で入れる以外に追跡へ反映する手段が無い。これが本ストーリーの起票理由である。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "tracker", roles = "TRACKER")
@DisplayName("US17 貨物状態を手動更新する")
class ManualStatusUpdateTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private String 追跡中の貨物(String trackingNumber, String status) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '山田物産株式会社', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq), "manual-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number)
                VALUES (?, ?, 'GENERAL', 1000.000, 'JPOSA', 'USLAX', ?,
                        'IN_TRANSIT', 'NOT_ROUTED', ?)
                """, bookingId, shipperId, LocalDate.now(clock).plusDays(60), trackingNumber);

        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version,
                    destination_unlocode, estimated_arrival_date)
                VALUES (?, ?, ?, 0, 'USLAX', ?)
                """, trackingNumber, bookingId, status, LocalDate.now(clock).plusDays(30));
        return trackingNumber;
    }

    private String 発生日時() {
        return LocalDate.now(clock).atTime(9, 0).toString();
    }

    /** 受入基準: 追跡番号を指定して現在の貨物情報を確認できる。 */
    @Test
    void 追跡詳細に手動更新の入力欄が出る() throws Exception {
        追跡中の貨物("TRK-20260801-9001", "LOADED");

        mockMvc.perform(get("/tracking/{n}", "TRK-20260801-9001"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("状態を手動で更新")))
                .andExpect(content().string(Matchers.containsString("出港")));
    }

    /** 受入基準: 新しい状態・位置・日時を入力して追跡情報を更新できる。 */
    @Test
    void 出港を手で入れると搭載中になる() throws Exception {
        追跡中の貨物("TRK-20260801-9002", "LOADED");

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9002")
                        .param("eventType", "DEPART")
                        .param("location", "JPOSA")
                        .param("occurredAt", 発生日時())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT transport_status FROM tracking_activity WHERE tracking_number = ?",
                String.class, "TRK-20260801-9002")).isEqualTo("ONBOARD_CARRIER");
    }

    /** 受入基準: 更新後、追跡イベントが履歴に記録される。 */
    @Test
    void 手動更新が履歴に残る() throws Exception {
        追跡中の貨物("TRK-20260801-9003", "LOADED");

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9003")
                .param("eventType", "DEPART")
                .param("location", "JPOSA")
                .param("occurredAt", 発生日時())
                .with(csrf()));

        var row = jdbcTemplate.queryForMap("""
                SELECT e.event_type, e.source, e.recorded_by
                  FROM tracking_handling_event e
                  JOIN tracking_activity t ON t.id = e.tracking_id
                 WHERE t.tracking_number = ?
                """, "TRK-20260801-9003");
        assertThat(row).containsEntry("event_type", "DEPART");
        // **荷役由来と手動を区別する。** 混ぜると「誰がいつ手で入れたか」を追えない
        assertThat(row).containsEntry("source", "MANUAL");
        assertThat(row).containsEntry("recorded_by", "tracker");
    }

    /**
     * <strong>入港は輸送状態を動かさない。</strong> 貨物の状態を変えるのは荷降ろしであり、
     * 入港は位置の記録に留める（通関と同じ扱い）。
     */
    @Test
    void 入港は位置だけを記録し状態を動かさない() throws Exception {
        追跡中の貨物("TRK-20260801-9004", "ONBOARD_CARRIER");

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9004")
                        .param("eventType", "ARRIVE")
                        .param("location", "USLAX")
                        .param("occurredAt", 発生日時())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT transport_status FROM tracking_activity WHERE tracking_number = ?",
                String.class, "TRK-20260801-9004")).isEqualTo("ONBOARD_CARRIER");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM tracking_handling_event e
                  JOIN tracking_activity t ON t.id = e.tracking_id
                 WHERE t.tracking_number = ?
                """, Integer.class, "TRK-20260801-9004")).isEqualTo(1);
    }

    /**
     * <strong>引取待ちへ移せる。</strong> 荷降ろし済みの貨物を引取待ちにするのは
     * 荷役作業ではなく、これまでどの経路からも設定できない状態だった。
     */
    @Test
    void 荷降ろし済みを引取待ちにできる() throws Exception {
        追跡中の貨物("TRK-20260801-9005", "UNLOADED");

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9005")
                .param("eventType", "AWAIT_CLAIM")
                .param("location", "USLAX")
                .param("occurredAt", 発生日時())
                .with(csrf()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT transport_status FROM tracking_activity WHERE tracking_number = ?",
                String.class, "TRK-20260801-9005")).isEqualTo("AWAITING_CLAIM");
    }

    /**
     * <strong>逆行は拒否する。</strong> 進んだ状態より前へ戻す手動更新は受け付けない。
     *
     * <p>戻す必要が生じるのは誤登録の訂正であり、それは US36（承認を伴う取り消し）で扱う。
     * 手動更新で黙って戻せると、<strong>引き渡し済みの貨物を輸送中に戻せてしまう。</strong>
     */
    @Test
    void 逆行する更新は拒否される() throws Exception {
        追跡中の貨物("TRK-20260801-9006", "AWAITING_CLAIM");

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9006")
                        .param("eventType", "DEPART")
                        .param("location", "JPOSA")
                        .param("occurredAt", 発生日時())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("flashError"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT transport_status FROM tracking_activity WHERE tracking_number = ?",
                String.class, "TRK-20260801-9006")).isEqualTo("AWAITING_CLAIM");
        // **拒否したら履歴も残さない。** 起きなかった出来事を記録しない
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM tracking_handling_event e
                  JOIN tracking_activity t ON t.id = e.tracking_id
                 WHERE t.tracking_number = ?
                """, Integer.class, "TRK-20260801-9006")).isZero();
    }

    /** 存在しない追跡番号は 404。**URL 直打ちで 500 にしない。** */
    @Test
    void 存在しない追跡番号は404になる() throws Exception {
        mockMvc.perform(post("/tracking/{n}/status", "TRK-19990101-0001")
                        .param("eventType", "DEPART")
                        .param("location", "JPOSA")
                        .param("occurredAt", 発生日時())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    /** マスタに無い港は受け付けない。**存在しない場所の記録は追跡の役に立たない。** */
    @Test
    void マスタに無い港は拒否される() throws Exception {
        追跡中の貨物("TRK-20260801-9007", "LOADED");

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9007")
                        .param("eventType", "DEPART")
                        .param("location", "XXYYZ")
                        .param("occurredAt", 発生日時())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("flashError"));
    }

    /** 追跡管理者だけが手動更新できる。 */
    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void 荷主は手動更新できない() throws Exception {
        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9008")
                        .param("eventType", "DEPART")
                        .param("location", "JPOSA")
                        .param("occurredAt", "2026-08-01T09:00")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    /** 荷主には手動更新の入力欄そのものを出さない。**押せない操作を見せない。** */
    @Test
    @WithMockUser(username = "shipper", roles = "SHIPPER")
    void 荷主には入力欄を出さない() throws Exception {
        追跡中の貨物("TRK-20260801-9009", "LOADED");

        mockMvc.perform(get("/tracking/{n}", "TRK-20260801-9009"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("状態を手動で更新"))));
    }

    /**
     * <strong>作業入口から対象にたどり着ける</strong>（T2 / 状態軸の到達性）。
     *
     * <p>状態を手で更新するには、まず対象の追跡番号に到達できなければならない。
     * 発行待ち一覧は発行した時点でその予約が消えるため、発行後の貨物へ画面から
     * 行く手段が無かった。<strong>追跡番号を覚えている追跡管理者はいない。</strong>
     */
    @Test
    void 追跡管理者の作業入口から追跡中の貨物へ行ける() throws Exception {
        追跡中の貨物("TRK-20260801-9011", "LOADED");
        // **期限が近いものから並ぶ**（朝に見るのは切羽詰まった順）。
        // 他のテストが作った貨物に押し出されないよう、最も急ぐ 1 件にする
        jdbcTemplate.update(
                "UPDATE cargo SET arrival_deadline = CURRENT_DATE - 1 WHERE tracking_number = ?",
                "TRK-20260801-9011");

        mockMvc.perform(get("/tracking/queue"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("追跡中の貨物")))
                .andExpect(content().string(Matchers.containsString("TRK-20260801-9011")))
                .andExpect(content().string(
                        Matchers.containsString("/tracking/TRK-20260801-9011")));
    }

    /**
     * 自動更新の取得先は <strong>{@code /status-fragment}</strong> である（IT7 の突合）。
     *
     * <p>同じパスを更新（POST・ROLE_TRACKER）と参照で共有すると、認可の対象が違うのに
     * 片方の規則しか効かない。
     */
    @Test
    void 自動更新のフラグメントは別のパスで取得する() throws Exception {
        追跡中の貨物("TRK-20260801-9010", "LOADED");

        mockMvc.perform(get("/tracking/{n}/status-fragment", "TRK-20260801-9010"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("積み込み済")));

        mockMvc.perform(get("/tracking/{n}", "TRK-20260801-9010"))
                .andExpect(content().string(Matchers.containsString("status-fragment")));
    }
}
