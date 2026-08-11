package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.CargoFixture;
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
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("山田物産株式会社")
                .arrivalDeadline(LocalDate.now(clock).plusDays(60))
                .status("IN_TRANSIT", "NOT_ROUTED")
                .trackingNumber(trackingNumber)
                .insert();
        UUID bookingId = cargo.bookingId();

        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version,
                    destination_unlocode, estimated_arrival_date)
                VALUES (?, ?, ?, 0, 'USLAX', ?)
                """, trackingNumber, bookingId, status, LocalDate.now(clock).plusDays(30));
        return trackingNumber;
    }

    /**
     * 発生日時の既定値。
     *
     * <p><strong>未来にしない。</strong> 手動更新は未来の日時を受け付けない（C10）ため、
     * 「今日の 9 時」を固定で使うと、業務時刻が 9 時より前の実行だけが落ちる。
     */
    private String 発生日時() {
        return java.time.LocalDateTime.now(clock).minusHours(1).withNano(0).toString();
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
     * 受入基準: <strong>状態変更の種類に応じて荷主への通知が送信される。</strong>
     *
     * <p>ADR-006 により外部へは送らない。**通知の実体は記録**であり（US12）、
     * 手動更新の履歴が予約詳細の通知履歴に現れることをもって満たす。
     *
     * <p><strong>Tracking から Booking を呼ばない。</strong> 呼ぶと ADR-012 で消した
     * Booking ⇄ Tracking の循環が戻る。起きた事実をイベントで伝え、
     * 通知の記録は Booking が自分で作る（ADR-009）。
     */
    @Test
    void 手動更新が荷主への通知記録として残る() throws Exception {
        String trackingNumber = 追跡中の貨物("TRK-20260801-9013", "LOADED");
        UUID bookingId = jdbcTemplate.queryForObject(
                "SELECT booking_id FROM cargo WHERE tracking_number = ?",
                UUID.class, trackingNumber);

        mockMvc.perform(post("/tracking/{n}/status", trackingNumber)
                .param("eventType", "DEPART")
                .param("location", "JPOSA")
                .param("occurredAt", 発生日時())
                .with(csrf()));

        var row = jdbcTemplate.queryForMap(
                "SELECT notification_type, content, sent_by, result"
                        + " FROM booking_notification WHERE booking_id = ?", bookingId);
        assertThat(row).containsEntry("notification_type", "STATUS_UPDATED");
        assertThat(row).containsEntry("sent_by", "tracker");
        // **何が起きたかを書く。** 「状態が変わりました」だけでは荷主は何も分からない
        assertThat((String) row.get("content")).contains("搭載中");
    }

    /** 状態が動かない種別（入港）では通知記録を作らない。**知らせる中身が無い。** */
    @Test
    void 状態が動かない更新では通知記録を作らない() throws Exception {
        String trackingNumber = 追跡中の貨物("TRK-20260801-9014", "ONBOARD_CARRIER");
        UUID bookingId = jdbcTemplate.queryForObject(
                "SELECT booking_id FROM cargo WHERE tracking_number = ?",
                UUID.class, trackingNumber);

        mockMvc.perform(post("/tracking/{n}/status", trackingNumber)
                .param("eventType", "ARRIVE")
                .param("location", "USLAX")
                .param("occurredAt", 発生日時())
                .with(csrf()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_notification WHERE booking_id = ?",
                Integer.class, bookingId)).isZero();
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

    /**
     * <strong>手動更新で選べない種別は、細工した POST でも受け付けない。</strong>
     *
     * <p>画面の選択肢から消すだけでは、リクエストを直接組み立てれば送れてしまう。
     * 荷役の記録（受領・積込・荷降し・引取）は現場の作業であり、
     * <strong>追跡管理者が机上で入れてよいものではない。</strong>
     */
    @Test
    void 荷役の種別は手動更新で登録できない() throws Exception {
        追跡中の貨物("TRK-20260801-9012", "RECEIVED");

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9012")
                        .param("eventType", "LOAD")
                        .param("location", "JPOSA")
                        .param("occurredAt", 発生日時())
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT transport_status FROM tracking_activity WHERE tracking_number = ?",
                String.class, "TRK-20260801-9012")).isEqualTo("RECEIVED");
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

    /**
     * <strong>C4: 引取が済んだ貨物に新しい出来事は起きない。</strong>
     *
     * <p>入港のように<strong>状態を動かさない種別は、逆行の検査を素通りする</strong>。
     * 引取完了の後に入港を入れられると、履歴に「引き取ったあとに船が着いた」という
     * 消せない矛盾が残る。訂正手段（US36）はまだ無い。
     */
    @Test
    void 引取済みの貨物には手動更新できない() throws Exception {
        追跡中の貨物("TRK-20260801-9020", "CLAIMED");

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9020")
                        .param("eventType", "ARRIVE")
                        .param("location", "USLAX")
                        .param("occurredAt", 発生日時())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("flashError"));

        assertThat(イベント件数("TRK-20260801-9020")).isZero();
    }

    /**
     * <strong>C10: 未来の日時は受け付けない。</strong>
     *
     * <p>まだ起きていない出来事を履歴に書くと、**予定と実績の区別が失われる**。
     * 打ち間違い（年を 1 つ多く打つ）は日常的に起きる。
     */
    @Test
    void 未来の発生日時は受け付けない() throws Exception {
        追跡中の貨物("TRK-20260801-9021", "LOADED");

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9021")
                        .param("eventType", "DEPART")
                        .param("location", "JPOSA")
                        .param("occurredAt", LocalDate.now(clock).plusDays(1).atTime(9, 0)
                                .toString())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("flashError"));

        assertThat(イベント件数("TRK-20260801-9021")).isZero();
    }

    /** 今この瞬間は受け付ける。**境界を「未来」の側に倒さない。** */
    @Test
    void 現在時刻の発生日時は受け付ける() throws Exception {
        追跡中の貨物("TRK-20260801-9022", "LOADED");

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9022")
                        .param("eventType", "DEPART")
                        .param("location", "JPOSA")
                        .param("occurredAt", java.time.LocalDateTime.now(clock)
                                .withNano(0).toString())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(イベント件数("TRK-20260801-9022")).isEqualTo(1);
    }

    /**
     * <strong>C10: 直前のイベントより過去には入れられない。</strong>
     *
     * <p>時系列が前後した履歴は、**どちらが本当に後なのかを誰も判断できない**。
     * 荷役由来の記録（後から入力しうる）と違い、手動更新は今の状況を入れる操作である。
     */
    @Test
    void 直前のイベントより過去の日時は受け付けない() throws Exception {
        追跡中の貨物("TRK-20260801-9023", "LOADED");
        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9023")
                .param("eventType", "DEPART")
                .param("location", "JPOSA")
                .param("occurredAt", 発生日時())
                .with(csrf()));

        mockMvc.perform(post("/tracking/{n}/status", "TRK-20260801-9023")
                        .param("eventType", "ARRIVE")
                        .param("location", "USLAX")
                        .param("occurredAt", LocalDate.now(clock).minusDays(1).atTime(9, 0)
                                .toString())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attributeExists("flashError"));

        // 出港の 1 件だけが残る
        assertThat(イベント件数("TRK-20260801-9023")).isEqualTo(1);
    }

    private Integer イベント件数(String trackingNumber) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM tracking_handling_event e
                  JOIN tracking_activity t ON t.id = e.tracking_id
                 WHERE t.tracking_number = ?
                """, Integer.class, trackingNumber);
    }
}
