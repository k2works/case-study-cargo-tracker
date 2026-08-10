package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 輸送料金を算出して確定する（US21 / US22）。
 *
 * <p>受入基準ごとに、その道を実行したテストを名指しする。
 *
 * <table>
 *   <caption>受入基準とテストの対応</caption>
 *   <tr><td>「引取済」状態の予約に対して料金算出を開始できる</td>
 *       <td>{@link #引取済みの貨物から料金を算出できる()}／
 *           拒む側: {@link #訂正申請中の貨物は請求対象に出ない()}</td></tr>
 *   <tr><td>輸送実績が表示される</td>
 *       <td>{@link #請求対象一覧に輸送実績が出る()}</td></tr>
 *   <tr><td>基本料金が自動計算される</td>
 *       <td>{@link #引取済みの貨物から料金を算出できる()}</td></tr>
 *   <tr><td>算出結果を確認して確定操作ができる</td>
 *       <td>{@link #確認してから確定できる()}</td></tr>
 *   <tr><td>確定後、輸送料金が「確定」状態で登録される</td>
 *       <td>{@link #確認してから確定できる()}／{@link #確定後は金額を動かせない()}</td></tr>
 *   <tr><td>例外がある場合、料金調整の入力ができる</td>
 *       <td>{@link #料金調整を入力できる()}</td></tr>
 *   <tr><td>法人荷主では契約割引率が自動的に取得・表示される（US22）</td>
 *       <td>{@link #法人荷主には契約割引率が適用される()}</td></tr>
 *   <tr><td>個人荷主の場合は割引が適用されない（US22）</td>
 *       <td>{@link #個人荷主には割引が適用されない()}</td></tr>
 *   <tr><td>割引計算の根拠が精算書に記載される（US22）</td>
 *       <td>{@link #法人荷主には契約割引率が適用される()}</td></tr>
 * </table>
 */
@AutoConfigureMockMvc
@DisplayName("輸送料金の算出と確定（US21 / US22）")
class ChargeCalculationScenarioTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 引取まで済んだ貨物を用意する。 */
    private UUID 引取済みの貨物(String trackingNumber, boolean corporate, String rate) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street, contract_number, discount_rate)
                VALUES (?, ?, ?, '請求テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1', ?, ?)
                """, shipperId, "SHP-%06d".formatted(seq),
                corporate ? "CORPORATE" : "INDIVIDUAL",
                "billing-%d@example.com".formatted(seq),
                corporate ? "CT-%06d".formatted(seq) : null,
                corporate ? new java.math.BigDecimal(rate) : java.math.BigDecimal.ZERO);

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number)
                VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'USLAX', CURRENT_DATE + 60,
                        'DELIVERED', 'ROUTED', ?)
                """, bookingId, shipperId, trackingNumber);

        Long cargoId = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0001', 'JPOSA', 'USLAX',
                        TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-20 09:00:00+09', 1)
                """, cargoId);
        jdbcTemplate.update("""
                INSERT INTO tracking_activity (
                    tracking_number, booking_id, transport_status, version,
                    destination_unlocode, estimated_arrival_date)
                VALUES (?, ?, 'CLAIMED', 0, 'USLAX', DATE '2026-04-20')
                """, trackingNumber, bookingId);
        return bookingId;
    }

    private String 請求対象一覧() throws Exception {
        return mockMvc.perform(get("/billing/pending").with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String 料金を算出する(UUID bookingId) throws Exception {
        String location = mockMvc.perform(post("/billing/invoices")
                        .param("bookingId", bookingId.toString())
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        return location == null ? "" : location.substring(location.lastIndexOf('/') + 1);
    }

    private String 請求書詳細(String invoiceNumber) throws Exception {
        return mockMvc.perform(get("/billing/invoices/{n}", invoiceNumber)
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 受入基準 1・3: 引取済みの貨物から料金を算出できる。 */
    @Test
    void 引取済みの貨物から料金を算出できる() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5001", false, null);

        String html = 請求書詳細(料金を算出する(bookingId));

        assertThat(html)
                .as("距離係数 1（区間 1 本）× 重量 1,000kg × 一般貨物 1.0 = 1,000 円")
                .contains("1,000 円");
        assertThat(html).contains("下書き");
    }

    /** 受入基準 2: 輸送実績が表示される。 */
    @Test
    void 請求対象一覧に輸送実績が出る() throws Exception {
        引取済みの貨物("TRK-20260601-5002", false, null);

        assertThat(請求対象一覧())
                .contains("TRK-20260601-5002")
                .contains("JPOSA → USLAX")
                .contains("請求テスト商事");
        assertThat(請求対象一覧())
                .as("**列挙子名を利用者に見せない**（レビュー H11）。"
                        + "CargoTypeFactor.displayName() は「見せない」と宣言しながら未使用だった")
                .contains("一般貨物")
                .doesNotContain(">GENERAL<");
    }

    /**
     * <strong>US22: 法人荷主には契約割引率が適用され、根拠が精算書に記載される。</strong>
     */
    @Test
    void 法人荷主には契約割引率が適用される() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5003", true, "0.1500");

        String html = 請求書詳細(料金を算出する(bookingId));

        assertThat(html)
                .as("割引率・基本料金・割引後料金の 3 つがそろって根拠になる")
                .contains("15.00 %")
                .contains("割引額")
                .contains("割引後料金");
    }

    /**
     * <strong>US22: 個人荷主には割引が適用されない。</strong>
     *
     * <p><strong>それでも割引の行は出る。</strong> 率 0% で同じ道を通しており、
     * 請求書の形は 1 種類のままである。
     */
    @Test
    void 個人荷主には割引が適用されない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5004", false, null);

        String html = 請求書詳細(料金を算出する(bookingId));

        assertThat(html).contains("0.00 %");
        assertThat(html)
                .as("行そのものは出る。出さないと請求書の形が 2 種類できる")
                .contains("法人契約割引の対象外");
    }

    /**
     * <strong>率の表示は百分率 2 桁に揃える</strong>（レビュー M6）。
     *
     * <p>税率は {@code NUMERIC(5,4)} で保持しており、そのまま 100 倍すると
     * <strong>「消費税（10.0000 %）」</strong>と出る。割引率は
     * {@code DiscountRate.asPercent()} が 2 桁に揃えており、
     * <strong>同じ「率 → 百分率」の変換に 2 つの答えがあった</strong>。
     *
     * <p>マニュアルにキャプチャを載せる以上、<strong>この見た目が正典になる</strong>。
     */
    @Test
    void 率の表示は百分率二桁に揃える() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5018", true, "0.1500");

        String html = 請求書詳細(料金を算出する(bookingId));

        assertThat(html).contains("10.00 %").doesNotContain("10.0000 %");
        assertThat(html).contains("15.00 %");
    }

    /** 受入基準 4・5: 算出結果を確認して確定できる。 */
    @Test
    void 確認してから確定できる() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5005", false, null);
        String invoiceNumber = 料金を算出する(bookingId);

        assertThat(請求書詳細(invoiceNumber))
                .as("算出しただけでは確定していない")
                .contains("下書き");

        mockMvc.perform(post("/billing/invoices/{n}/confirmation", invoiceNumber)
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(請求書詳細(invoiceNumber)).contains("確定");
    }

    /**
     * <strong>確定後は金額を動かせない。</strong>
     *
     * <p>確定は経理担当者が金額を承認した印である。後から動かせるなら、
     * <strong>確定という操作に意味が無い</strong>。
     * 画面から調整の欄が消えることと、POST しても拒まれることを対で見る。
     */
    @Test
    void 確定後は金額を動かせない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5006", false, null);
        String invoiceNumber = 料金を算出する(bookingId);
        mockMvc.perform(post("/billing/invoices/{n}/confirmation", invoiceNumber)
                        .with(user("billing1").roles("BILLING")).with(csrf()));

        assertThat(請求書詳細(invoiceNumber))
                .as("確定後は調整の入口を出さない")
                .doesNotContain("/adjustment");

        mockMvc.perform(post("/billing/invoices/{n}/adjustment", invoiceNumber)
                        .param("reduction", "100").param("compensation", "0")
                        .param("reason", "後出しの減額")
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_amount_value FROM invoice WHERE invoice_number = ?",
                Integer.class, invoiceNumber))
                .as("見せていないだけでなく、実際に動かない")
                .isEqualTo(1100);
    }

    /** 受入基準 6: 料金調整を入力できる。 */
    @Test
    void 料金調整を入力できる() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5007", false, null);
        String invoiceNumber = 料金を算出する(bookingId);

        mockMvc.perform(post("/billing/invoices/{n}/adjustment", invoiceNumber)
                        .param("reduction", "200").param("compensation", "100")
                        .param("reason", "遅延による減額と代替輸送費")
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(請求書詳細(invoiceNumber))
                .as("(1,000 - 200 + 100) × 1.10 = 990")
                .contains("990 円")
                .contains("遅延による減額と代替輸送費");
    }

    /**
     * <strong>画面の内訳の足し算が合う</strong>（レビュー H1）。
     *
     * <p>経理担当者はこの表を電卓で検算する。<strong>足し算が合わない表は、
     * それだけで請求全体が信用されない。</strong>
     *
     * <p><strong>法人（割引あり）かつ料金調整ありの組み合わせで壊れていた。</strong>
     * 計算順序は 基本料金 → 調整 → 割引 → 消費税 であり、割引は調整後の額に掛かる。
     * 画面が「基本料金 − 割引額」で割引後料金を作ると、調整の分だけずれる。
     * <strong>調整ありのテストは個人荷主（割引 0%）しか無く、判別できなかった。</strong>
     */
    @Test
    void 割引と調整が同時にあっても内訳の足し算が合う() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5015", true, "0.1500");
        String invoiceNumber = 料金を算出する(bookingId);

        mockMvc.perform(post("/billing/invoices/{n}/adjustment", invoiceNumber)
                        .param("reduction", "100").param("compensation", "50")
                        .param("reason", "遅延による減額と代替輸送費")
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // 基本 1,000 → 調整後 950 → 割引 15% で 807（切り捨て）→ 税 80 → 総額 887
        assertThat(請求書詳細(invoiceNumber))
                .as("割引後料金は調整後の額に割引を掛けた値である")
                .contains("807 円")
                .contains("887 円");

        Integer total = jdbcTemplate.queryForObject(
                "SELECT total_amount_value FROM invoice WHERE invoice_number = ?",
                Integer.class, invoiceNumber);
        Integer tax = jdbcTemplate.queryForObject(
                "SELECT tax_amount_value FROM invoice WHERE invoice_number = ?",
                Integer.class, invoiceNumber);
        assertThat(total - tax)
                .as("画面に出す割引後料金と保存値が一致する")
                .isEqualTo(807);
    }

    /**
     * <strong>訂正・取り消しの申請中は請求対象に出ない</strong>（IT12 持ち越し C8）。
     *
     * <p>取り消されるかもしれない引取をもとに請求書を出すと、
     * 出した後で引取が無かったことになる。
     * <strong>申請前は出ることを対で見る</strong> — 一律に隠す実装では緑にならない。
     */
    @Test
    void 訂正申請中の貨物は請求対象に出ない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5008", false, null);

        assertThat(請求対象一覧())
                .as("申請前は請求対象に出る")
                .contains("TRK-20260601-5008");

        jdbcTemplate.update("""
                INSERT INTO handling_activity (
                    booking_id, event_type, event_completion_time,
                    location_unlocode, operator_name, version,
                    claim_confirmation_method, claim_confirmation_code, claim_consignee_name)
                VALUES (?, 'CLAIM', TIMESTAMP WITH TIME ZONE '2026-04-20 09:00:00+09',
                        'USLAX', '荷役太郎', 0, 'CODE', 'CLM-A1B2C3D9', '受取次郎')
                """, bookingId);
        Long handlingId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM handling_activity WHERE booking_id = ?",
                Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO handling_correction (
                    handling_activity_id, request_type, reason,
                    requested_by, requested_at, status, version)
                VALUES (?, 'CANCEL', '取り違えの疑い', '荷役太郎',
                        TIMESTAMP WITH TIME ZONE '2026-04-21 09:00:00+09', 'PENDING', 0)
                """, handlingId);

        assertThat(請求対象一覧())
                .as("申請中は消える。取り消されるかもしれない引取は請求できない")
                .doesNotContain("TRK-20260601-5008");
    }

    /**
     * <strong>請求済みの貨物は請求対象に出ない。</strong>
     *
     * <p>二重請求の入口を画面に置かない。
     */
    @Test
    void 請求済みの貨物は請求対象に出ない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5009", false, null);
        料金を算出する(bookingId);

        assertThat(請求対象一覧()).doesNotContain("TRK-20260601-5009");
    }

    /**
     * <strong>算出できない理由が画面に表示される</strong>（T1 の数え上げで見つかった穴）。
     *
     * <p>マニュアルは「引取が済んでいない貨物・訂正の申請中の貨物・すでに請求済みの
     * 貨物は算出できません。<strong>理由が画面に表示されます</strong>」と書いている。
     * <strong>拒むことは単体テストで確かめていたが、理由が画面に届く道を
     * 実行したテストが無かった。</strong>
     */
    @Test
    void 算出できない理由が画面に表示される() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5012", false, null);
        料金を算出する(bookingId);

        // 2 回目は請求済みで拒まれる
        String location = mockMvc.perform(post("/billing/invoices")
                        .param("bookingId", bookingId.toString())
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashError",
                        "すでに請求書が作成されています"))
                .andReturn().getResponse().getHeader("Location");

        assertThat(location)
                .as("行き止まりにしない。請求対象一覧へ戻す")
                .endsWith("/billing/pending");
    }

    /**
     * <strong>理由の無い料金調整は反映されない</strong>（T1 の数え上げで見つかった穴）。
     *
     * <p>マニュアルは「理由が空だと反映できません」と書いている。
     * <strong>値オブジェクトが拒むことは確かめていたが、画面から空の理由を
     * 送った道を実行したテストが無かった。</strong>
     * <strong>500 にせず、理由をそのまま画面へ返す。</strong>
     */
    @Test
    void 理由の無い料金調整は反映されない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5013", false, null);
        String invoiceNumber = 料金を算出する(bookingId);

        mockMvc.perform(post("/billing/invoices/{n}/adjustment", invoiceNumber)
                        .param("reduction", "100").param("compensation", "0")
                        .param("reason", " ")
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("flashError"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_amount_value FROM invoice WHERE invoice_number = ?",
                Integer.class, invoiceNumber))
                .as("金額は動かない")
                .isEqualTo(1100);
    }

    /**
     * <strong>請求額を超える減額は画面からも通らない</strong>（T1 の数え上げで見つかった穴）。
     *
     * <p>マニュアルは「請求額を超える減額はできません。返金は精算の取り消しを伴う
     * 別の業務です」と書いている。<strong>ドメインが拒むことは確かめていたが、
     * 画面から送った道を実行したテストが無かった。</strong>
     */
    @Test
    void 請求額を超える減額は画面からも通らない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5014", false, null);
        String invoiceNumber = 料金を算出する(bookingId);

        mockMvc.perform(post("/billing/invoices/{n}/adjustment", invoiceNumber)
                        .param("reduction", "999999").param("compensation", "0")
                        .param("reason", "過大な減額")
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("flashError"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_amount_value FROM invoice WHERE invoice_number = ?",
                Integer.class, invoiceNumber))
                .as("負の請求書を黙って作らない")
                .isEqualTo(1100);
    }

    /**
     * <strong>請求書は経理担当者にしか見せない。</strong>
     *
     * <p>金額であり、見える範囲を誤ると他社の取引条件が漏れる。
     * <strong>請求書が存在する状態で確かめる</strong> — 空の一覧では判別しない。
     */
    @Test
    void 経理担当者以外は請求書を開けない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5010", false, null);
        String invoiceNumber = 料金を算出する(bookingId);

        mockMvc.perform(get("/billing/invoices/{n}", invoiceNumber)
                        .with(user("sales1").roles("SALES")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/billing/pending").with(user("shipper1").roles("SHIPPER")))
                .andExpect(status().isForbidden());
    }

    /**
     * <strong>経理担当者がダッシュボードと navbar から請求対象へ到達できる。</strong>
     *
     * <p>「気づく手段」だけでは仕事は進まない。件数から対象へ行けることまで要る。
     */
    @Test
    void 経理担当者が請求対象に到達できる() throws Exception {
        引取済みの貨物("TRK-20260601-5011", false, null);

        String dashboard = mockMvc.perform(get("/")
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(dashboard)
                .as("navbar とカードの両方から行ける")
                .contains("/billing/pending")
                .contains("未請求の引取済貨物");
    }

    /**
     * <strong>経理担当者以外のダッシュボードには請求のカードを出さない。</strong>
     *
     * <p>これが無いと、常にカードを出す実装でも上のテストが緑になる。
     */
    @Test
    void 経理担当者以外に請求のカードを出さない() throws Exception {
        String dashboard = mockMvc.perform(get("/")
                        .with(user("sales1").roles("SALES")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(dashboard).doesNotContain("未請求の引取済貨物");
    }
}
