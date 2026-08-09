package com.example.cargotracker.routing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 運航変更に<strong>気づく手段</strong>（IT11 の C9 / C7）。
 *
 * <p>US25 そのもの（差分の確認と確定）は {@link VoyageRescheduleTest} が受け持つ。
 * 本テストが確かめるのは<strong>更新したあとに何が残るか</strong>である。
 *
 * <p>経路の区間は<strong>割り当てたときの写し</strong>であり、航海の日程を更新しても
 * 書き換わらない（確定した経路を勝手に作り直さないため）。そのため予約詳細は
 * <strong>古い日時を表示し続ける</strong>。気づく手段が無いまま古い日付を
 * 見せるのがいちばん悪い。
 *
 * <p><strong>他のテストと同じ港を使わない。</strong> 同じ港で登録すると、
 * その港で検索している他のテストの期待する便を 1 ページ目から押し出す。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "router", roles = "ROUTER")
@DisplayName("運航変更に気づく手段（C9 / C7）")
class VoyageRescheduleImpactTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private String voyageNumber;

    /** <strong>絶対値で書かない。</strong> 固定日を過ぎると出港済みの検査に当たる。 */
    private LocalDateTime 出発() {
        return LocalDateTime.now(clock).plusDays(40).withHour(9).withMinute(0)
                .withSecond(0).withNano(0);
    }

    private LocalDateTime 到着() {
        return 出発().plusDays(11);
    }

    @BeforeEach
    void 航海を登録する() throws Exception {
        voyageNumber = "V" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(送信("/voyages", 登録フォーム()))
                .andExpect(status().is3xxRedirection());
    }

    private Map<String, String> 登録フォーム() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("voyageNumber", voyageNumber);
        values.put("vesselName", "かもめ丸");
        values.put("carrierName", "南洋海運");
        values.put("cargoTypes", "GENERAL");
        values.put("capacityWeightKg", "100000");
        values.put("movements[0].departure", "CLVAP");
        values.put("movements[0].arrival", "CAVAN");
        values.put("movements[0].departureTime", 出発().toString());
        values.put("movements[0].arrivalTime", 到着().toString());
        return values;
    }

    /** 出発が 3 日遅れる運航変更。 */
    private Map<String, String> 変更フォーム() {
        Map<String, String> values = 登録フォーム();
        values.put("movements[0].departureTime", 出発().plusDays(3).toString());
        values.put("movements[0].arrivalTime", 到着().plusDays(3).toString());
        return values;
    }

    private MockHttpServletRequestBuilder 送信(String path, Map<String, String> values) {
        var request = post(path).with(csrf()).with(user("router").roles("ROUTER"));
        values.forEach(request::param);
        return request;
    }

    /** この便を経路に含む予約を 1 件作る。**追跡番号は任意**（状態軸の出し分けに使う）。 */
    private UUID この便を使う予約を作る(String shipperName, String trackingNumber) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', ?, ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq), shipperName,
                "impact-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number)
                VALUES (?, ?, 'GENERAL', 1000.000, 'CLVAP', 'CAVAN', ?,
                        'CONFIRMED', 'ROUTED', ?)
                """, bookingId, shipperId,
                LocalDateTime.now(clock).plusDays(70).toLocalDate(), trackingNumber);

        Long cargoId = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, ?, 'CLVAP', 'CAVAN', ?, ?, 0)
                """, cargoId, voyageNumber,
                java.sql.Timestamp.from(出発().atZone(clock.getZone()).toInstant()),
                java.sql.Timestamp.from(到着().atZone(clock.getZone()).toInstant()));
        return bookingId;
    }

    private void 日程をずらす() throws Exception {
        mockMvc.perform(送信("/voyages/" + voyageNumber, 変更フォーム()))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * <strong>日程が変わった区間に印が出て、そこから遅延例外の登録へ行ける</strong>
     * （C9。IT10 で計画どおり落としたもの）。
     *
     * <p>荷主にとって「いつ着くのか」は最も知りたいことである。
     * <strong>印を出すだけでは足りず、そこから次の行動へ繋ぐ</strong>
     * （IT9 の Try T2）。
     */
    @Test
    void 日程が変わった区間に印が出て遅延例外の登録へ行ける() throws Exception {
        UUID bookingId = この便を使う予約を作る("印テスト商事", "TRK-20261101-9501");

        日程をずらす();

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("tracker").roles("TRACKER")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("日程が変わりました")))
                .andExpect(content().string(Matchers.containsString("/tracking/exceptions/new")));
    }

    /**
     * <strong>変わっていなければ印は出さない。</strong>
     *
     * <p>「出ること」だけを確かめると、常に印を出す実装でも緑になる。
     * 全区間に印が出れば、印そのものが読み飛ばされる。
     */
    @Test
    void 日程が変わっていなければ印は出さない() throws Exception {
        UUID bookingId = この便を使う予約を作る("印なし商事", "TRK-20261101-9502");

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("tracker").roles("TRACKER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("日程が変わりました"))));
    }

    /**
     * <strong>追跡番号がまだ無い予約では、行けない先へ誘わない</strong>（状態軸の到達性）。
     *
     * <p>例外は追跡番号で起票する。番号が出ていない予約でリンクを出すと、
     * 押した先で入力できないフォームが開く。<strong>印は出したうえで、
     * いま何ができるのかを言う。</strong>
     */
    @Test
    void 追跡番号が無ければ登録の導線ではなく理由を出す() throws Exception {
        UUID bookingId = この便を使う予約を作る("番号なし商事", null);

        日程をずらす();

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("tracker").roles("TRACKER")))
                .andExpect(status().isOk())
                // まず「開けたこと」を見る（IT10 の Try T3）
                .andExpect(content().string(Matchers.containsString("日程が変わりました")))
                .andExpect(content().string(Matchers.containsString("追跡番号の発行後に")))
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("/tracking/exceptions/new"))));
    }

    /**
     * <strong>更新したあとも「連絡が要る仕事」の件数が残る</strong>（C7）。
     *
     * <p>確認画面には影響する予約の件数が出るが、<strong>確定した瞬間に消えていた</strong>。
     * 更新は終わりではなく、荷主への連絡という次の仕事の始まりである。
     */
    @Test
    void 更新後のフラッシュに影響件数が残る() throws Exception {
        この便を使う予約を作る("連絡先商事", "TRK-20261101-9503");

        mockMvc.perform(送信("/voyages/" + voyageNumber, 変更フォーム()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashSuccess", Matchers.containsString("1 件")));
    }

    /** <strong>影響する予約が無ければ件数を言わない。</strong> 0 件を数えても仕事は無い。 */
    @Test
    void 影響する予約が無ければ件数は出さない() throws Exception {
        mockMvc.perform(送信("/voyages/" + voyageNumber, 変更フォーム()))
                .andExpect(flash().attribute("flashSuccess",
                        Matchers.not(Matchers.containsString("件"))));
    }
}
