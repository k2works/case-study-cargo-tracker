package com.example.cargotracker.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 通関を通してから引き渡す（US29）。
 *
 * <p><strong>通関は業務上の唯一の「止まる仕組み」である。</strong> 誤配も荷受人違いも
 * 「起きた事実」として記録するが、通関前の引き渡しは<strong>実行してはならない</strong>。
 *
 * <p>ここで壊すのは入口と出口の両方である。<strong>拒まれること</strong>（入口）だけでなく、
 * <strong>引取の記録が残らないこと・予約が配送完了にならないこと</strong>（出口）まで見る。
 * 「エラーを出した」と「実行されなかった」は別である。
 *
 * <p>港は既存のテストが使っていない組み合わせ（KRPUS / USSEA）を使う。
 */
@AutoConfigureMockMvc
@DisplayName("通関を通してから引き渡す（US29）")
class CustomsClearanceScenarioTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 荷降しまで進んだ貨物を用意する（通関と引取の前提）。 */
    private UUID 荷降し済みの貨物(String trackingNumber) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '通関テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq),
                "customs-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number, consignee_name)
                VALUES (?, ?, 'GENERAL', 1000, 'KRPUS', 'USSEA', CURRENT_DATE + 60,
                        'IN_TRANSIT', 'ROUTED', ?, '受取花子')
                """, bookingId, shipperId, trackingNumber);

        // **経路を割り当て済にするなら旅程も要る。** 集約が「割り当て済の貨物には
        // 旅程が必要です」と拒む（テストデータも本番と同じ形で作る）
        Long cargoId = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0088', 'KRPUS', 'USSEA',
                        TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-19 09:00:00+09', 1)
                """, cargoId);

        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'UNLOADED', 0)
                """, trackingNumber, bookingId);
        return bookingId;
    }

    /** 通関の荷役を登録する（申告はこの作業に紐づく）。 */
    private ResultActions 通関の荷役を登録する(String trackingNumber) throws Exception {
        return mockMvc.perform(post("/handling")
                .param("trackingNumber", trackingNumber)
                .param("type", "CUSTOMS")
                .param("completionTime", "2026-04-20T09:00")
                .param("locationUnlocode", "USSEA")
                .param("operatorName", "港湾太郎")
                .with(user("handler").roles("HANDLER")).with(csrf()));
    }

    private ResultActions 申告を登録する(String trackingNumber, String declarationNumber)
            throws Exception {
        return mockMvc.perform(post("/handling/customs")
                .param("trackingNumber", trackingNumber)
                .param("declarationNumber", declarationNumber)
                .param("declaredAt", "2026-04-20T09:30")
                .with(user("handler").roles("HANDLER")).with(csrf()));
    }

    private ResultActions 状態を更新する(long declarationId, String status, String reason)
            throws Exception {
        return mockMvc.perform(post("/handling/customs/{id}/status", declarationId)
                .param("status", status)
                .param("reason", reason)
                .with(user("handler").roles("HANDLER")).with(csrf()));
    }

    private ResultActions 引取を登録する(String trackingNumber) throws Exception {
        return mockMvc.perform(post("/handling")
                .param("trackingNumber", trackingNumber)
                .param("type", "CLAIM")
                .param("completionTime", "2026-04-21T10:00")
                .param("locationUnlocode", "USSEA")
                .param("confirmationCode", "123456")
                .param("consigneeName", "受取花子")
                .param("operatorName", "港湾太郎")
                .with(user("handler").roles("HANDLER")).with(csrf()));
    }

    private long 申告の識別子(String trackingNumber) {
        return jdbcTemplate.queryForObject("""
                SELECT d.id FROM customs_declaration d
                  JOIN handling_activity h ON h.id = d.handling_activity_id
                 WHERE h.tracking_number = ?
                """, Long.class, trackingNumber);
    }

    private int 引取の件数(String trackingNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handling_activity "
                        + "WHERE tracking_number = ? AND event_type = 'CLAIM'",
                Integer.class, trackingNumber);
    }

    /** 受入基準: 追跡番号・申告番号・申告日時を入力して登録できる（初期状態は審査中）。 */
    @Test
    void 申告を登録すると審査中で始まる() throws Exception {
        String number = "TRK-20260420-8101";
        荷降し済みの貨物(number);
        通関の荷役を登録する(number);

        申告を登録する(number, "DEC-8101").andExpect(status().is3xxRedirection());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM customs_declaration WHERE declaration_number = ?",
                String.class, "DEC-8101")).isEqualTo("PENDING");
    }

    /**
     * <strong>通関の荷役が無ければ、理由を示して拒む。</strong>
     *
     * <p>申告は「どの荷役でどの貨物を通したか」の記録である。貨物に紐づかない申告は
     * 業務上あり得ない。<strong>500 にせず、次にすることを言う。</strong>
     */
    @Test
    void 通関の荷役が無ければ理由を示して拒む() throws Exception {
        String number = "TRK-20260420-8102";
        荷降し済みの貨物(number);

        申告を登録する(number, "DEC-8102")
                .andExpect(flash().attribute("flashError",
                        Matchers.containsString("通関の荷役")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customs_declaration WHERE declaration_number = ?",
                Integer.class, "DEC-8102")).isZero();
    }

    /**
     * 受入基準: <strong>通関済でない貨物への引取は拒否され、現在の通関状態が提示される。</strong>
     *
     * <p><strong>「エラーを出した」と「実行されなかった」は別である。</strong>
     * 引取の記録が残らないことまで確かめる。
     */
    @Test
    void 通関前の引取は拒否され記録も残らない() throws Exception {
        String number = "TRK-20260420-8103";
        荷降し済みの貨物(number);
        通関の荷役を登録する(number);
        申告を登録する(number, "DEC-8103");

        // **拒否は入力の差し戻しとして返す**（荷役登録は他の拒否も同じ形）
        引取を登録する(number)
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("通関が完了していない")))
                // 現在の状態を示す（「なぜ止まっているか」が分かる）
                .andExpect(content().string(Matchers.containsString("審査中")));

        assertThat(引取の件数(number)).isZero();
    }

    /**
     * <strong>申告が無くても、通関が要る貨物の引取は拒む</strong>（C29）。
     *
     * <p>IT11 は「申告が登録されている貨物」にしか拒否が効かず、
     * <strong>申告を出し忘れた輸入貨物は引取が通っていた</strong>。
     * 実務では<strong>出し忘れている貨物こそ引き取らせてはいけない</strong>。
     */
    @Test
    void 申告が無くても通関が要る貨物の引取は拒む() throws Exception {
        String number = "TRK-20260420-8113";
        荷降し済みの貨物(number);
        // 通関の荷役も申告も無い（KRPUS → USSEA は国をまたぐ）

        引取を登録する(number)
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("通関")));

        assertThat(引取の件数(number)).isZero();
    }

    /**
     * <strong>国内輸送では通関を求めない。</strong>
     *
     * <p>「拒むこと」だけを確かめると、常に拒む実装でも緑になる。
     * その実装だと<strong>国内輸送の引取がすべて止まる</strong>。
     */
    @Test
    void 国内輸送では申告が無くても引取できる() throws Exception {
        String number = "TRK-20260420-8114";
        UUID bookingId = 荷降し済みの貨物(number);
        // 同じ国の中で完結する輸送に変える（KRPUS → KRPUS では区間が成立しないため
        // 目的地だけを同国の別港にする）
        jdbcTemplate.update(
                "UPDATE cargo SET origin_unlocode = 'USLAX', destination_unlocode = 'USSEA' "
                        + "WHERE booking_id = ?", bookingId);
        jdbcTemplate.update(
                "UPDATE leg SET load_location_unlocode = 'USLAX', "
                        + "unload_location_unlocode = 'USSEA' WHERE cargo_id = "
                        + "(SELECT id FROM cargo WHERE booking_id = ?)", bookingId);

        引取を登録する(number).andExpect(status().is3xxRedirection());

        assertThat(引取の件数(number)).isEqualTo(1);
    }

    /** 受入基準: 通関済に更新すると引取できる。**通したことも確かめる。** */
    @Test
    void 通関済にすると引取できる() throws Exception {
        String number = "TRK-20260420-8104";
        荷降し済みの貨物(number);
        通関の荷役を登録する(number);
        申告を登録する(number, "DEC-8104");

        状態を更新する(申告の識別子(number), "CLEARED", "書類に不備なし")
                .andExpect(status().is3xxRedirection());
        引取を登録する(number).andExpect(status().is3xxRedirection());

        assertThat(引取の件数(number)).isEqualTo(1);
    }

    /** 受入基準: 通関済になると荷主・荷受人に通関完了が通知される（記録として満たす）。 */
    @Test
    void 通関済になると通知が記録される() throws Exception {
        String number = "TRK-20260420-8105";
        荷降し済みの貨物(number);
        通関の荷役を登録する(number);
        申告を登録する(number, "DEC-8105");

        状態を更新する(申告の識別子(number), "CLEARED", "問題なし");

        var types = jdbcTemplate.queryForList("""
                SELECT n.notification_type FROM booking_notification n
                  JOIN cargo c ON c.booking_id = n.booking_id
                 WHERE c.tracking_number = ?
                """, String.class, number);
        assertThat(types).contains("CUSTOMS_CLEARED");
    }

    /**
     * 受入基準: 通関済になると<strong>荷主・荷受人</strong>に通知される。
     *
     * <p><strong>括弧の中まで読む</strong>（IT10 の Try T5）。「荷主に」ではなく
     * 「荷主・荷受人に」と書いてある。荷受人は引き取りに来る当人であり、
     * 通関が下りたことを最も待っている。
     */
    @Test
    void 通関済になると荷受人にも通知が記録される() throws Exception {
        String number = "TRK-20260420-8111";
        UUID bookingId = 荷降し済みの貨物(number);
        jdbcTemplate.update(
                "UPDATE cargo SET consignee_email = ? WHERE booking_id = ?",
                "consignee-8111@example.com", bookingId);
        通関の荷役を登録する(number);
        申告を登録する(number, "DEC-8111");

        状態を更新する(申告の識別子(number), "CLEARED", "問題なし");

        var recipients = jdbcTemplate.queryForList("""
                SELECT n.recipient_email FROM booking_notification n
                 WHERE n.booking_id = ? AND n.notification_type = 'CUSTOMS_CLEARED'
                """, String.class, bookingId);
        assertThat(recipients).contains("consignee-8111@example.com");
        assertThat(recipients).hasSize(2);
    }

    /**
     * <strong>荷受人の連絡先が無ければ、荷主にだけ送る。</strong>
     *
     * <p>荷受人は予約の時点では未確定でありうる（US16）。
     * 未登録を理由に荷主への記録まで落とさない。
     */
    @Test
    void 荷受人の連絡先が無ければ荷主にだけ記録される() throws Exception {
        String number = "TRK-20260420-8112";
        UUID bookingId = 荷降し済みの貨物(number);
        通関の荷役を登録する(number);
        申告を登録する(number, "DEC-8112");

        状態を更新する(申告の識別子(number), "CLEARED", "問題なし");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_notification
                 WHERE booking_id = ? AND notification_type = 'CUSTOMS_CLEARED'
                """, Integer.class, bookingId)).isEqualTo(1);
    }

    /** 受入基準: <strong>留置にすると例外種別「税関保留」が自動起票される。</strong> */
    @Test
    void 留置にすると税関保留の例外が自動起票される() throws Exception {
        String number = "TRK-20260420-8106";
        荷降し済みの貨物(number);
        通関の荷役を登録する(number);
        申告を登録する(number, "DEC-8106");

        状態を更新する(申告の識別子(number), "HELD", "書類不備のため保留");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT e.exception_type FROM tracking_exception_event e
                  JOIN tracking_activity t ON t.id = e.tracking_id
                 WHERE t.tracking_number = ?
                """, String.class, number)).isEqualTo("CUSTOMS_HOLD");
    }

    /** 受入基準: 変更履歴（日時・変更者・理由）が残る。 */
    @Test
    void 状態の変更履歴が残る() throws Exception {
        String number = "TRK-20260420-8107";
        荷降し済みの貨物(number);
        通関の荷役を登録する(number);
        申告を登録する(number, "DEC-8107");

        状態を更新する(申告の識別子(number), "HELD", "検査のため保留");

        var row = jdbcTemplate.queryForMap(
                "SELECT * FROM customs_status_history WHERE declaration_id = ?",
                申告の識別子(number));
        assertThat(row.get("status_from")).isEqualTo("PENDING");
        assertThat(row.get("status_to")).isEqualTo("HELD");
        assertThat(row.get("reason").toString()).contains("検査");
        assertThat(row.get("changed_by")).isEqualTo("handler");
    }

    /**
     * <strong>1 貨物に 2 つの申告を作らない。</strong>
     *
     * <p>どちらが引取の可否を決めるのかが決まらなくなる。
     *
     * <p><strong>数え上げで空振りが見つかった検査である</strong>（IT11 の Try T1）。
     * 拒否を書いても、通る道が試されていなければ守られているとは言えない。
     */
    @Test
    void 同じ貨物に申告を二重登録できない() throws Exception {
        String number = "TRK-20260420-8109";
        荷降し済みの貨物(number);
        通関の荷役を登録する(number);
        申告を登録する(number, "DEC-8109");

        申告を登録する(number, "DEC-8109-2")
                .andExpect(flash().attribute("flashError",
                        Matchers.containsString("すでに通関申告が登録されています")));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM customs_declaration d
                  JOIN handling_activity h ON h.id = d.handling_activity_id
                 WHERE h.tracking_number = ?
                """, Integer.class, number)).isEqualTo(1);
    }

    /**
     * <strong>通関以外の荷役では申告を登録できない。</strong>
     *
     * <p>申告は「どの<strong>通関</strong>作業でどの貨物を通したか」の記録である。
     * 受領の作業に紐づけると、通関していない貨物に申告が付く。
     *
     * <p><strong>数え上げで空振りが見つかった検査である</strong>（IT11 の Try T1）。
     * 荷役が 1 件も無い場合だけを試していたため、
     * <strong>種別の絞り込みを外しても赤にならなかった</strong>。
     */
    @Test
    void 通関以外の荷役しか無ければ申告を登録できない() throws Exception {
        String number = "TRK-20260420-8110";
        荷降し済みの貨物(number);
        // 受領だけを登録する（通関はまだ）
        mockMvc.perform(post("/handling")
                .param("trackingNumber", number)
                .param("type", "RECEIVE")
                .param("completionTime", "2026-04-20T08:00")
                .param("locationUnlocode", "KRPUS")
                .param("operatorName", "港湾太郎")
                .with(user("handler").roles("HANDLER")).with(csrf()));

        申告を登録する(number, "DEC-8110")
                .andExpect(flash().attribute("flashError",
                        Matchers.containsString("通関の荷役")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customs_declaration WHERE declaration_number = ?",
                Integer.class, "DEC-8110")).isZero();
    }

    /** <strong>理由の無い更新は受け付けない。</strong> なぜ止めたのかが残らない。 */
    @Test
    void 理由の無い更新は受け付けない() throws Exception {
        String number = "TRK-20260420-8108";
        荷降し済みの貨物(number);
        通関の荷役を登録する(number);
        申告を登録する(number, "DEC-8108");

        状態を更新する(申告の識別子(number), "HELD", "  ")
                .andExpect(flash().attribute("flashError",
                        Matchers.containsString("理由")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM customs_declaration WHERE declaration_number = ?",
                String.class, "DEC-8108")).isEqualTo("PENDING");
    }
}
