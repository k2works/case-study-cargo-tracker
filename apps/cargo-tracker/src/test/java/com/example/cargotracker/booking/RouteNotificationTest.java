package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * US12: 確定経路を荷主に通知する。受入基準に 1:1 で対応させる。
 *
 * <p><strong>「送ったつもり」を検知できることが本機能の目的である。</strong>
 * 送信操作だけを実装して履歴を残さないと、荷主から「聞いていない」と言われたときに
 * 確認する手段が無い。
 *
 * <p>ADR-006 により<strong>外部への送信は行わない</strong>（内部シミュレーション）。
 * 通知の実体は<strong>記録</strong>であり、本テストが確かめるのも記録の側である。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "sales", roles = "SALES")
@DisplayName("US12 確定経路を荷主に通知する")
class RouteNotificationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 業務のクロック。**JVM 既定の now() を使うと CI（UTC）でだけずれる。** */
    @Autowired
    private java.time.Clock clock;

    /** 経路が確定した予約を 1 件作る。**通知できるのはこの状態からである。** */
    private UUID 経路確定済みの予約(String shipperEmail) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '山田物産株式会社', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """,
                shipperId, "SHP-%06d".formatted(seq), shipperEmail);

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status)
                VALUES (?, ?, 'GENERAL', 1000.000, 'JPOSA', 'USLAX', ?,
                        'ROUTE_PROPOSED', 'ROUTED')
                """,
                bookingId, shipperId, LocalDate.now(clock).plusDays(40));

        // **旅程が無いと「割り当て済」は成立しない**（IT7 の教訓）
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, seq_number, voyage_number,
                    load_location_unlocode, unload_location_unlocode,
                    load_time, unload_time)
                SELECT c.id, 1, 'V0042', 'JPOSA', 'USLAX',
                       CURRENT_TIMESTAMP + INTERVAL '5 days',
                       CURRENT_TIMESTAMP + INTERVAL '20 days'
                  FROM cargo c WHERE c.booking_id = ?
                """, bookingId);
        return bookingId;
    }

    private List<Map<String, Object>> 通知履歴(UUID bookingId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM booking_notification WHERE booking_id = ? ORDER BY id", bookingId);
    }

    /** 受入基準: 予約番号を指定して、紐付けられた経路情報を確認できる。 */
    @Test
    void 通知内容をプレビューできる() throws Exception {
        var bookingId = 経路確定済みの予約("preview@example.com");

        mockMvc.perform(get("/bookings/{id}/notifications/new", bookingId))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/notification"))
                // 受入基準: 経由港・所要日数・到着予定日
                .andExpect(content().string(Matchers.containsString("V0042")))
                .andExpect(content().string(Matchers.containsString("所要日数")))
                .andExpect(content().string(Matchers.containsString("到着予定")))
                // 送信先を確認してから送る
                .andExpect(content().string(Matchers.containsString("preview@example.com")));
    }

    /** 受入基準: 荷主への経路通知を送信でき、送信記録が登録される。 */
    @Test
    void 通知を送ると履歴に残る() throws Exception {
        var bookingId = 経路確定済みの予約("send@example.com");

        mockMvc.perform(post("/bookings/{id}/notifications", bookingId).with(csrf()))
                .andExpect(status().is3xxRedirection());

        var history = 通知履歴(bookingId);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst()).containsEntry("recipient_email", "send@example.com");
        assertThat(history.getFirst()).containsEntry("result", "SUCCEEDED");
        assertThat(history.getFirst()).containsEntry("sent_by", "sales");
        assertThat((String) history.getFirst().get("content")).contains("V0042");
    }

    /** 送信記録は予約詳細に常時表示される。**残しても見えなければ確認できない。** */
    @Test
    void 送信記録が予約詳細に出る() throws Exception {
        var bookingId = 経路確定済みの予約("history@example.com");
        mockMvc.perform(post("/bookings/{id}/notifications", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}", bookingId))
                .andExpect(content().string(Matchers.containsString("通知履歴")))
                .andExpect(content().string(Matchers.containsString("history@example.com")))
                .andExpect(content().string(Matchers.containsString("経路確定")));
    }

    /** 一度も通知していない予約でも、履歴の欄そのものは出る（「まだ送っていない」が分かる）。 */
    @Test
    void 未通知でも履歴の欄が出る() throws Exception {
        var bookingId = 経路確定済みの予約("none@example.com");

        mockMvc.perform(get("/bookings/{id}", bookingId))
                .andExpect(content().string(Matchers.containsString("通知履歴")))
                .andExpect(content().string(Matchers.containsString("まだ通知していません")));
    }

    /**
     * <strong>期限を延ばして割り当てた場合、その差分を通知に含める</strong>
     * （`ui_design.md` 経路割り当て §候補ゼロ時の再算出）。
     *
     * <p>荷主が知る必要があるのは「いつ着くか」だけではない。
     * <strong>当初の約束から何日ずれたか</strong>である。
     */
    @Test
    void 期限を延ばしていればその差分が通知に載る() throws Exception {
        var bookingId = 経路確定済みの予約("relaxed@example.com");
        LocalDate original = LocalDate.now(clock).plusDays(30);
        jdbcTemplate.update("""
                INSERT INTO booking_route_proposal (
                    booking_id, origin_unlocode, destination_unlocode,
                    arrival_deadline, original_arrival_deadline,
                    max_transit_count, calculation_count, candidate_count,
                    cargo_type, weight)
                VALUES (?, 'JPOSA', 'USLAX', ?, ?, 2, 2, 1, 'GENERAL', 1000.000)
                """, bookingId, original.plusDays(7), original);

        mockMvc.perform(get("/bookings/{id}/notifications/new", bookingId))
                .andExpect(content().string(Matchers.containsString("当初の希望期限")))
                .andExpect(content().string(Matchers.containsString("7 日")));

        mockMvc.perform(post("/bookings/{id}/notifications", bookingId).with(csrf()));

        assertThat((String) 通知履歴(bookingId).getFirst().get("content")).contains("7 日");
    }

    /**
     * <strong>経路が確定していない予約は通知できない。</strong>
     * 送るべき中身が無い通知を「送信済み」として記録すると、履歴が信用できなくなる。
     */
    @Test
    void 経路が確定していなければ通知できない() throws Exception {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '未確定商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq), "notrouted-%d@example.com".formatted(seq));
        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status)
                VALUES (?, ?, 'GENERAL', 1000.000, 'JPOSA', 'USLAX', ?,
                        'ROUTE_PROPOSED', 'NOT_ROUTED')
                """, bookingId, shipperId, LocalDate.now(clock).plusDays(40));

        // **理由をそのまま返す。** 「通知できません」だけでは何を直せばよいか分からない。
        // フラッシュ属性はリダイレクト応答そのもので確かめる
        // （別リクエストの GET には引き継がれない）
        mockMvc.perform(post("/bookings/{id}/notifications", bookingId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("flashError", "経路が確定していないため通知できません"));

        assertThat(通知履歴(bookingId)).isEmpty();
    }

    /** 経路が確定していない予約には、そもそも通知のボタンを出さない。 */
    @Test
    void 経路未確定の予約には通知ボタンを出さない() throws Exception {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '未確定商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq), "nobtn-%d@example.com".formatted(seq));
        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status)
                VALUES (?, ?, 'GENERAL', 1000.000, 'JPOSA', 'USLAX', ?,
                        'ROUTE_PROPOSED', 'NOT_ROUTED')
                """, bookingId, shipperId, LocalDate.now(clock).plusDays(40));

        mockMvc.perform(get("/bookings/{id}", bookingId))
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("荷主に経路を通知"))));
    }

    /** 経路設計者は通知しない。**荷主とのやり取りは営業の仕事である。** */
    @Test
    @WithMockUser(username = "router", roles = "ROUTER")
    void 経路設計者は通知できない() throws Exception {
        var bookingId = 経路確定済みの予約("router@example.com");

        mockMvc.perform(post("/bookings/{id}/notifications", bookingId).with(csrf()))
                .andExpect(status().isForbidden());
    }

    /**
     * <strong>営業に「通知すべき予約」の入口がある</strong>（US12 の作業入口）。
     *
     * <p>経路を割り当てるのは経路設計者であり、ADR-006 により通知も送られない。
     * 入口が無いと、通知すべき予約を探すには予約を 1 件ずつ開いて履歴を見るしかなく、
     * <strong>「送ったつもり」を検知するという目的が運用で壊れる。</strong>
     */
    @Test
    void 通知待ちの一覧から通知に進める() throws Exception {
        var bookingId = 経路確定済みの予約("queue@example.com");
        // 期限の近い順に並ぶ。他のテストが作った予約に押し出されないようにする
        jdbcTemplate.update(
                "UPDATE cargo SET arrival_deadline = CURRENT_DATE - 1 WHERE booking_id = ?",
                bookingId);

        mockMvc.perform(get("/bookings/notification-queue"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("荷主への通知待ち")))
                .andExpect(content().string(
                        Matchers.containsString("/bookings/" + bookingId + "/notifications/new")));
    }

    /** 通知した予約は一覧から消える。**やることが残っていないものを並べない。** */
    @Test
    void 通知済みの予約は通知待ちから消える() throws Exception {
        var bookingId = 経路確定済みの予約("done@example.com");
        jdbcTemplate.update(
                "UPDATE cargo SET arrival_deadline = CURRENT_DATE - 1 WHERE booking_id = ?",
                bookingId);
        mockMvc.perform(post("/bookings/{id}/notifications", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/notification-queue"))
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("/bookings/" + bookingId + "/notifications/new"))));
    }

    /** 通知は営業の仕事である。**経路設計者の一覧ではない。** */
    @Test
    @WithMockUser(username = "router", roles = "ROUTER")
    void 経路設計者は通知待ちの一覧を開けない() throws Exception {
        mockMvc.perform(get("/bookings/notification-queue"))
                .andExpect(status().isForbidden());
    }

    /** 同じ予約に 2 回通知したら、履歴は 2 件になる。**上書きしない。** */
    @Test
    void 再通知は履歴に積まれる() throws Exception {
        var bookingId = 経路確定済みの予約("twice@example.com");

        mockMvc.perform(post("/bookings/{id}/notifications", bookingId).with(csrf()));
        mockMvc.perform(post("/bookings/{id}/notifications", bookingId).with(csrf()));

        assertThat(通知履歴(bookingId)).hasSize(2);
    }
}
