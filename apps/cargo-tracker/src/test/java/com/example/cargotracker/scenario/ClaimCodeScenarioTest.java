package com.example.cargotracker.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.LogCapture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 引取確認コードを採番して照合する（US35）。
 *
 * <p>IT7 の引取記録は<strong>提示された値をそのまま書き写すだけ</strong>で、
 * 照合する相手がシステムの中に無かった。<strong>記録はできるが証明にならない。</strong>
 *
 * <p>受入基準ごとに、その道を実行したテストを名指しする（IT11 のふりかえり T6）。
 *
 * <table>
 *   <caption>受入基準とテストの対応</caption>
 *   <tr><td>予約確定時に採番される</td>
 *       <td>{@link #予約を確定すると引取確認コードが採番される()}</td></tr>
 *   <tr><td>予約詳細で確認でき、荷主へ伝えられる</td>
 *       <td>{@link #採番されたコードを予約詳細で読める()}</td></tr>
 *   <tr><td>一致しないコードでは記録できない</td>
 *       <td>{@link #一致しないコードでは引取を記録できない()}</td></tr>
 *   <tr><td>一致しなかった事実が監査ログに残る</td>
 *       <td>{@link #一致しなかった事実が監査ログに残る()}</td></tr>
 *   <tr><td>追跡番号とは別の値である</td>
 *       <td>{@link #追跡番号を入れても引き取れない()}</td></tr>
 * </table>
 */
@AutoConfigureMockMvc
@DisplayName("引取確認コードを採番して照合する（US35）")
class ClaimCodeScenarioTest extends PostgreSQLIntegrationTestBase {

    private static final String CONSIGNEE = "受取花子";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 経路が確定し、確定を待っている予約を用意する。 */
    private UUID 確定待ちの予約() {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '引取コードテスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq),
                "claim-code-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, consignee_name)
                VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'JPTYO', CURRENT_DATE + 60,
                        'ROUTE_PROPOSED', 'ROUTED', ?)
                """, bookingId, shipperId, CONSIGNEE);
        Long cargoId = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0066', 'JPOSA', 'JPTYO',
                        TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-19 09:00:00+09', 1)
                """, cargoId);
        return bookingId;
    }

    private ResultActions 確定する(UUID bookingId) throws Exception {
        return mockMvc.perform(post("/bookings/{id}/confirm", bookingId)
                .with(user("sales").roles("SALES")).with(csrf()));
    }

    private String 採番されたコード(UUID bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT claim_code FROM cargo WHERE booking_id = ?", String.class, bookingId);
    }

    /** 荷降しまで進める（引取の前提）。 */
    private void 荷降しまで進める(UUID bookingId, String trackingNumber) {
        // **追跡番号は確定した予約に発行する**（集約の不変条件）。確定より後に付ける
        jdbcTemplate.update(
                "UPDATE cargo SET booking_status = 'IN_TRANSIT', tracking_number = ? "
                        + "WHERE booking_id = ?", trackingNumber, bookingId);
        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version)
                VALUES (?, ?, 'UNLOADED', 0)
                """, trackingNumber, bookingId);
    }

    private ResultActions 引取を登録する(String trackingNumber, String code) throws Exception {
        return mockMvc.perform(post("/handling")
                .param("trackingNumber", trackingNumber)
                .param("type", "CLAIM")
                .param("completionTime", "2026-04-21T10:00")
                .param("locationUnlocode", "JPTYO")
                .param("confirmationCode", code)
                .param("consigneeName", CONSIGNEE)
                .param("operatorName", "港湾太郎")
                .with(user("handler").roles("HANDLER")).with(csrf()));
    }

    private int 引取の件数(String trackingNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handling_activity "
                        + "WHERE tracking_number = ? AND event_type = 'CLAIM'",
                Integer.class, trackingNumber);
    }

    /** 受入基準: 予約確定時（US13）に引取確認コードが採番される。 */
    @Test
    void 予約を確定すると引取確認コードが採番される() throws Exception {
        UUID bookingId = 確定待ちの予約();

        確定する(bookingId);

        assertThat(採番されたコード(bookingId)).matches("CLM-[0-9A-Z]{8}");
    }

    /**
     * 受入基準: 採番されたコードを予約詳細で確認でき、営業担当者が荷主へ伝えられる。
     *
     * <p><strong>荷主も読める。</strong> 伝えるのは営業担当者だが、
     * <strong>荷主が自分で確かめられないと、引取当日に電話が要る</strong>。
     */
    @Test
    void 採番されたコードを予約詳細で読める() throws Exception {
        UUID bookingId = 確定待ちの予約();
        確定する(bookingId);
        String code = 採番されたコード(bookingId);

        String html = mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("sales").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("引取確認コード").contains(code);
    }

    /**
     * <strong>荷主は自分の予約でコードを読める</strong>（US35 の受入基準 2 の系）。
     *
     * <p>伝えるのは営業担当者だが、<strong>荷主が自分で確かめられないと
     * 引取当日に電話が要る</strong>。全ロールから隠す実装だと上のテストは緑になるため、
     * 見えるべき相手に見えることを対で固定する。
     */
    @Test
    void 荷主は自分の予約で引取確認コードを読める() throws Exception {
        UUID bookingId = 確定待ちの予約();
        確定する(bookingId);
        String code = 採番されたコード(bookingId);
        UUID shipperId = jdbcTemplate.queryForObject(
                "SELECT shipper_id FROM cargo WHERE booking_id = ?", UUID.class, bookingId);

        String html = mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(com.example.cargotracker.support.ShipperScopedTestUser.scopedTo(shipperId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains(code);
    }

    /**
     * <strong>荷役登録の画面が「照合する」と言っている。</strong>
     *
     * <p>IT7 の画面は「システムでの照合は行いません」と案内していた。当時は正解が
     * 存在せず正しかったが、US35 で照合するようになった。
     * <strong>現場はマニュアルより画面を読む。</strong> 画面の案内が実装より
     * 古いままだと、作業員は「適当でよい」と読んで拒否され、理由が分からない。
     *
     * <p><strong>マニュアルを直したときに画面も直したかを、ここで固定する。</strong>
     * 宣言と実装の食い違いは、対象を変えて再発する。
     */
    @Test
    void 荷役登録の画面は照合すると案内する() throws Exception {
        String html = mockMvc.perform(get("/handling/new")
                        .with(user("handler").roles("HANDLER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("システムが照合します");
        assertThat(html)
                .as("IT7 の「照合しません」が残っていてはならない")
                .doesNotContain("照合は行いません");
    }

    /**
     * <strong>引取確認コードは伝える人と受け取る側だけが読む。</strong>
     *
     * <p>予約詳細は経路設計者・追跡管理者にも開いている（引き渡された予約の内容を
     * 確認するため）。<strong>しかしコードは引き渡しの証明である。</strong>
     * 業務上必要のないロールに見せると、<strong>コードを知る人が増えるほど
     * 証明の価値が下がる</strong>。
     *
     * <p>経路設計者は経路を選ぶために予約を読む。追跡管理者は発行の対象を確かめる。
     * <strong>どちらも引き渡しには関わらない。</strong>
     */
    @Test
    void 引取確認コードは経路設計者と追跡管理者には出ない() throws Exception {
        UUID bookingId = 確定待ちの予約();
        確定する(bookingId);
        String code = 採番されたコード(bookingId);

        for (String role : new String[] {"ROUTER", "TRACKER"}) {
            String html = mockMvc.perform(get("/bookings/{id}", bookingId)
                            .with(user(role.toLowerCase(java.util.Locale.ROOT)).roles(role)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html)
                    .as("%s には引取確認コードを見せない", role)
                    .doesNotContain(code);
        }
    }

    /**
     * <strong>公開追跡には出さない。</strong>
     *
     * <p>追跡番号は取引先へ転送される。そこに確認コードが並んでいたら、
     * <strong>転送された相手が誰でも引き取れる</strong>。
     */
    @Test
    void 公開追跡には引取確認コードを出さない() throws Exception {
        UUID bookingId = 確定待ちの予約();
        確定する(bookingId);
        String code = 採番されたコード(bookingId);
        荷降しまで進める(bookingId, "TRK-20260420-6203");

        String html = mockMvc.perform(get("/public/tracking/{n}", "TRK-20260420-6203"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain(code);
    }

    /**
     * 受入基準: 入力されたコードが採番済みのコードと一致しない場合、記録できない。
     *
     * <p>拒んだこと（入口）だけでなく、<strong>引取が記録されないこと</strong>
     * （出口）まで見る。
     */
    @Test
    void 一致しないコードでは引取を記録できない() throws Exception {
        UUID bookingId = 確定待ちの予約();
        確定する(bookingId);
        荷降しまで進める(bookingId, "TRK-20260420-6204");

        引取を登録する("TRK-20260420-6204", "CLM-99999999");

        assertThat(引取の件数("TRK-20260420-6204"))
                .as("一致しないコードで引き渡してはならない").isZero();
    }

    /**
     * <strong>一致すれば記録できる。</strong>
     *
     * <p>全部を拒む実装でも上の 1 件は緑になる。
     * その実装だと<strong>誰も引き取れない</strong>。
     */
    @Test
    void 一致するコードなら引取を記録できる() throws Exception {
        UUID bookingId = 確定待ちの予約();
        確定する(bookingId);
        String code = 採番されたコード(bookingId);
        荷降しまで進める(bookingId, "TRK-20260420-6205");

        引取を登録する("TRK-20260420-6205", code);

        assertThat(引取の件数("TRK-20260420-6205")).isEqualTo(1);
    }

    /** 受入基準: 一致しなかった事実が監査ログに残る（総当たりの検知）。 */
    @Test
    void 一致しなかった事実が監査ログに残る() throws Exception {
        UUID bookingId = 確定待ちの予約();
        確定する(bookingId);
        荷降しまで進める(bookingId, "TRK-20260420-6206");

        try (LogCapture audit = LogCapture.of("audit.handling")) {
            引取を登録する("TRK-20260420-6206", "CLM-88888888");

            assertThat(audit.messages())
                    .as("総当たりは「多数の失敗」として現れる")
                    .anySatisfy(message -> assertThat(message)
                            .contains("拒否")
                            .contains("TRK-20260420-6206"));
            // **入力されたコードそのものは残さない。**
            // ログの閲覧権限が引取の権限になってはならない
            assertThat(String.join("\n", audit.messages()))
                    .doesNotContain("CLM-88888888");
        }
    }

    /**
     * 受入基準: コードは追跡番号とは別の値である
     * （<strong>追跡番号を知っているだけでは引き取れない</strong>）。
     */
    @Test
    void 追跡番号を入れても引き取れない() throws Exception {
        UUID bookingId = 確定待ちの予約();
        確定する(bookingId);
        荷降しまで進める(bookingId, "TRK-20260420-6207");

        引取を登録する("TRK-20260420-6207", "TRK-20260420-6207");

        assertThat(引取の件数("TRK-20260420-6207"))
                .as("追跡番号は合鍵であって引取の証明ではない").isZero();
    }
}
