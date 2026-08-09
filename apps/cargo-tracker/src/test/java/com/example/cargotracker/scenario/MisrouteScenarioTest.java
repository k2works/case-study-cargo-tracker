package com.example.cargotracker.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 誤配を検知して経路を組み直す（US28）。
 *
 * <p><strong>誤配は記録して終わりではない。</strong> 貨物は予定と違う港にあり、
 * そこから目的地までの経路を引き直さないと動かない。
 *
 * <p>受入基準は「登録<strong>前</strong>に警告が表示される」「警告を<strong>承認して</strong>
 * 登録すると」と書いている。<strong>これは順序の要求である</strong> — 登録したあとに
 * 「予定と違いました」と伝えるのでは、作業員は取り消す手段を持たない。
 *
 * <p>港は既存のテストが使っていない組み合わせ（ESVLC / ITGOA / FRLEH）を使う。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "handler", roles = "HANDLER")
@DisplayName("誤配の検知と経路の再設計（US28）")
class MisrouteScenarioTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    /**
     * 追跡番号。<strong>発行日を番号に埋め込む形式である</strong>ため業務の今日から作る。
     *
     * <p><strong>連番の若い側を使わない。</strong> 本番の採番は 0001 から始まり、
     * テストが先に埋めると同じ日に発行するだけの他のテストが UNIQUE 制約で落ちる。
     */
    private String 追跡番号(int suffix) {
        return "TRK-%s-%04d".formatted(
                LocalDate.now(clock).toString().replace("-", ""), 9800 + suffix);
    }

    /** 経路が確定し、追跡番号まで出た貨物を用意する（誤配の前提）。 */
    private UUID 輸送中の貨物(String trackingNumber) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '誤配テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq),
                "misroute-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number, consignee_name)
                VALUES (?, ?, 'GENERAL', 1000, 'ESVLC', 'ITGOA', ?,
                        'IN_TRANSIT', 'ROUTED', ?, '受取花子')
                """, bookingId, shipperId,
                LocalDate.now(clock).plusDays(30), trackingNumber);

        Long cargoId = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0099', 'ESVLC', 'ITGOA', ?, ?, 1)
                """, cargoId,
                java.sql.Timestamp.from(clock.instant().plusSeconds(24 * 3600)),
                java.sql.Timestamp.from(clock.instant().plusSeconds(10 * 24 * 3600)));

        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version,
                    destination_unlocode, estimated_arrival_date)
                VALUES (?, ?, 'RECEIVED', 0, 'ITGOA', ?)
                """, trackingNumber, bookingId, LocalDate.now(clock).plusDays(10));
        return bookingId;
    }

    /**
     * 希望期限が明日に迫った貨物。<strong>どんな経路でも間に合わない。</strong>
     *
     * <p>期限を絶対日付で書かず、業務の今日から作る。
     */
    private UUID 期限が迫った貨物(String trackingNumber) {
        UUID bookingId = 輸送中の貨物(trackingNumber);
        jdbcTemplate.update(
                "UPDATE cargo SET arrival_deadline = ? WHERE booking_id = ?",
                LocalDate.now(clock).plusDays(1), bookingId);
        return bookingId;
    }

    /**
     * 現在地から目的地へ向かう便を 1 本用意し、経路候補を算出する。
     *
     * <p><strong>候補が無ければ超過日数を出しようがない。</strong> 画面に何も出ない
     * ことを「超過が出ていない」と読むと、テストが空振りする。
     */
    private void 現在地からの候補を算出する(UUID bookingId) throws Exception {
        String voyageNumber = "V" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/voyages")
                .param("voyageNumber", voyageNumber)
                .param("vesselName", "みなと丸")
                .param("carrierName", "地中海海運")
                .param("cargoTypes", "GENERAL")
                .param("capacityWeightKg", "100000")
                .param("movements[0].departure", "FRLEH")
                .param("movements[0].arrival", "ITGOA")
                .param("movements[0].departureTime",
                        LocalDateTime.now(clock).plusDays(2).withHour(9)
                                .withMinute(0).withSecond(0).withNano(0).toString())
                .param("movements[0].arrivalTime",
                        LocalDateTime.now(clock).plusDays(12).withHour(9)
                                .withMinute(0).withSecond(0).withNano(0).toString())
                .with(user("router").roles("ROUTER")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId)
                .with(user("router").roles("ROUTER")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    /** 予定に無い港で積み込む。**承認していない状態で送る。** */
    private ResultActions 予定外の積込を送る(String trackingNumber, boolean acknowledged)
            throws Exception {
        var request = post("/handling")
                .param("trackingNumber", trackingNumber)
                .param("type", "LOAD")
                .param("completionTime",
                        LocalDateTime.now(clock).minusHours(1).withSecond(0).withNano(0).toString())
                // 予定に無い港（旅程は ESVLC → ITGOA）
                .param("locationUnlocode", "FRLEH")
                .param("voyageNumber", "V0099")
                .param("operatorName", "港湾太郎")
                .with(user("handler").roles("HANDLER")).with(csrf());
        if (acknowledged) {
            request = request.param("acknowledged", "true");
        }
        return mockMvc.perform(request);
    }

    private int 荷役の件数(String trackingNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handling_activity WHERE tracking_number = ?",
                Integer.class, trackingNumber);
    }

    private String 経路状態(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT routing_status FROM cargo WHERE booking_id = ?",
                String.class, bookingId);
    }

    @Nested
    @DisplayName("登録前の警告と承認")
    class 登録前の警告と承認 {

        /**
         * 受入基準: <strong>作業場所が予定ルートに含まれない場合、登録前に警告される。</strong>
         *
         * <p><strong>「エラーを出した」と「実行されなかった」は別である。</strong>
         * 荷役が登録されていないことまで確かめる。
         */
        @Test
        void 予定ルート外の積込は登録前に警告される() throws Exception {
            String number = 追跡番号(1);
            輸送中の貨物(number);

            予定外の積込を送る(number, false)
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("予定ルートに無い")))
                    // 承認の操作がそこにある（次にすることを示す）
                    .andExpect(content().string(Matchers.containsString("承認して登録")));

            assertThat(荷役の件数(number)).isZero();
        }

        /**
         * 受入基準: <strong>承認して登録すると経路状態が「誤配」になり、
         * 例外種別「誤配」が自動起票される。</strong>
         */
        @Test
        void 承認して登録すると誤配になり例外が起票される() throws Exception {
            String number = 追跡番号(2);
            UUID bookingId = 輸送中の貨物(number);

            予定外の積込を送る(number, true)
                    .andExpect(status().is3xxRedirection());

            assertThat(荷役の件数(number)).isEqualTo(1);
            assertThat(経路状態(bookingId)).isEqualTo("MISROUTED");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT e.exception_type FROM tracking_exception_event e
                      JOIN tracking_activity t ON t.id = e.tracking_id
                     WHERE t.tracking_number = ?
                    """, String.class, number)).isEqualTo("MISROUTED");
        }

        /**
         * <strong>予定どおりの積込は 1 段階のまま。</strong>
         *
         * <p>承認を毎回挟むと、現場の作業が倍になり、警告そのものが読み飛ばされる。
         */
        @Test
        void 予定どおりの積込は承認を求めない() throws Exception {
            String number = 追跡番号(3);
            輸送中の貨物(number);

            mockMvc.perform(post("/handling")
                    .param("trackingNumber", number)
                    .param("type", "LOAD")
                    .param("completionTime",
                            LocalDateTime.now(clock).minusHours(1)
                                    .withSecond(0).withNano(0).toString())
                    .param("locationUnlocode", "ESVLC")
                    .param("voyageNumber", "V0099")
                    .param("operatorName", "港湾太郎")
                    .with(user("handler").roles("HANDLER")).with(csrf()))
                    .andExpect(status().is3xxRedirection());

            assertThat(荷役の件数(number)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("予約詳細からの再設計")
    class 予約詳細からの再設計 {

        /**
         * 受入基準: <strong>予約詳細に誤配の警告バナーが表示され、
         * 検知した荷役イベント（場所・日時）と現在地が示される。</strong>
         */
        @Test
        void 予約詳細に誤配のバナーと現在地が出る() throws Exception {
            String number = 追跡番号(4);
            UUID bookingId = 輸送中の貨物(number);
            予定外の積込を送る(number, true);

            mockMvc.perform(get("/bookings/{id}", bookingId)
                            .with(user("tracker").roles("TRACKER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("誤配")))
                    // 検知した荷役の場所（＝貨物の現在地）
                    .andExpect(content().string(Matchers.containsString("FRLEH")));
        }

        /**
         * 受入基準: <strong>経路設計者は予約詳細から現在地を出発地とした
         * 経路割り当て画面へ遷移できる。</strong>
         */
        @Test
        void 経路設計者は現在地を出発地として再設計できる() throws Exception {
            String number = 追跡番号(5);
            UUID bookingId = 輸送中の貨物(number);
            予定外の積込を送る(number, true);

            mockMvc.perform(get("/bookings/{id}", bookingId)
                            .with(user("router").roles("ROUTER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("経路を再設計")));

            mockMvc.perform(get("/bookings/{id}/route", bookingId)
                            .with(user("router").roles("ROUTER")))
                    .andExpect(status().isOk())
                    // 現在地から引き直すことを画面が明示する
                    .andExpect(content().string(
                            Matchers.containsString("誤配のため現在地 FRLEH から再設計")));
        }

        /**
         * 受入基準: <strong>再設計後の到着が当初の希望期限を超える場合、
         * その差分が明示される。</strong>
         *
         * <p>「期限を過ぎます」だけでは、<strong>1 日なのか 2 週間なのかが分からない</strong>。
         * 荷主に説明するのも、代替を探すかを決めるのも、その日数で変わる。
         */
        @Test
        void 期限を超える候補には超過日数が出る() throws Exception {
            String number = 追跡番号(7);
            UUID bookingId = 期限が迫った貨物(number);
            予定外の積込を送る(number, true);
            現在地からの候補を算出する(bookingId);

            mockMvc.perform(get("/bookings/{id}/route", bookingId)
                            .with(user("router").roles("ROUTER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("日超過")));
        }

        /**
         * <strong>期限に間に合う候補には超過日数を出さない。</strong>
         *
         * <p>「出ること」だけを確かめると、常に出す実装でも緑になる。
         */
        @Test
        void 期限に間に合えば超過日数は出さない() throws Exception {
            String number = 追跡番号(8);
            UUID bookingId = 輸送中の貨物(number);
            予定外の積込を送る(number, true);
            現在地からの候補を算出する(bookingId);

            mockMvc.perform(get("/bookings/{id}/route", bookingId)
                            .with(user("router").roles("ROUTER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("誤配のため現在地")))
                    .andExpect(content().string(
                            Matchers.not(Matchers.containsString("日超過"))));
        }

        /**
         * <strong>誤配でない予約では、予約詳細に誤配のバナーを出さない。</strong>
         *
         * <p>「出ること」だけを確かめると、常に出す実装でも緑になる。
         *
         * <p><strong>まず開けたことを見る</strong>（IT10 の Try T3）。要素の不在は、
         * 画面が壊れていても成立する。
         */
        @Test
        void 誤配でなければバナーも再設計の導線も出さない() throws Exception {
            String number = 追跡番号(6);
            UUID bookingId = 輸送中の貨物(number);

            mockMvc.perform(get("/bookings/{id}", bookingId)
                            .with(user("router").roles("ROUTER")))
                    .andExpect(status().isOk())
                    // 開けたことを確かめてから、無いことを見る
                    .andExpect(content().string(Matchers.containsString("予約詳細")))
                    .andExpect(content().string(
                            Matchers.not(Matchers.containsString("経路を再設計"))));
        }
    }
}
