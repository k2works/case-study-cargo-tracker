package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.CargoFixture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 月次の締めに要る情報が請求の画面に出ること（IT13 レビュー C1・C2）。
 *
 * <p><strong>本クラスは {@code ChargeCalculationScenarioTest} から切り出した。</strong>
 * 500 行の上限に当たったのは合図である。<strong>料金をどう計算して確定するか</strong>と、
 * <strong>経理が毎月どう締めるか</strong>は別の関心である。前者は 1 件の貨物の話であり、
 * 後者は一覧全体の話である。
 */
@AutoConfigureMockMvc
@DisplayName("月次の締めに要る情報（C1 / C2）")
class BillingMonthlyClosingTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * <strong>請求対象一覧に引取日が出る</strong>（C1）。
     *
     * <p>経理の月次は「<strong>前月に引取が済んだ分</strong>」を締める作業である。
     * 日付が無いと、いま並んでいる貨物が前月分か当月分か判別できず、
     * <strong>締め日をまたいだ引取が混ざったまま確定すると当月の売上計上が狂う</strong>。
     */
    @Test
    void 請求対象一覧に引取日が出る() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260601-5019");
        jdbcTemplate.update(
                "UPDATE cargo SET claimed_at = TIMESTAMP WITH TIME ZONE "
                        + "'2026-04-20 09:00:00+09' WHERE booking_id = ?", bookingId);

        assertThat(請求対象一覧())
                .as("いつ引取が済んだかが読める")
                .contains("2026-04-20");
    }

    /**
     * <strong>引取日が無い貨物でも一覧は開ける</strong>（C1）。
     *
     * <p>列が無かったころに引取が済んだ貨物は値を持たない。
     * <strong>拒むと、その貨物のせいで一覧ごと開けなくなる</strong>
     * （「不変条件の追加は既存行を壊す」の型）。
     */
    @Test
    void 引取日が無い貨物も一覧に並ぶ() throws Exception {
        引取済みの貨物("TRK-20260601-5020");

        assertThat(請求対象一覧())
                .as("**日付が無いことを「不明」と伝える。** 行を落とすと請求漏れになる")
                .contains("TRK-20260601-5020")
                .contains("不明");
    }

    /**
     * <strong>請求書一覧に件数と合計が出る</strong>（C2）。
     *
     * <p>経理が月次で最初にすることは「<strong>今月いくら請求したか</strong>」の確認である。
     * 1 行ずつ電卓で足すのは締めの作業ではない。<strong>絞り込んだ結果の合計</strong>が
     * その場に出ていて初めて、総勘定元帳と突き合わせられる。
     *
     * <p><strong>絞り込みに追随する合計であることまで見る。</strong> 全件の合計を
     * 出しっぱなしにすると、確定分だけを見ているつもりで下書きを含んだ額を
     * 元帳と比べることになる。
     */
    @Test
    void 請求書一覧に件数と合計が出る() throws Exception {
        // **他のテストが作った請求書と混ざる。** 絶対値ではなく増分で見る
        締め allBefore = 締めを読む(null);
        締め confirmedBefore = 締めを読む("CONFIRMED");

        請求書を作る(引取済みの貨物("TRK-20260601-5021"), "CONFIRMED", 1100);
        請求書を作る(引取済みの貨物("TRK-20260601-5022"), "CONFIRMED", 2200);
        請求書を作る(引取済みの貨物("TRK-20260601-5023"), "DRAFT", 5500);

        締め allAfter = 締めを読む(null);
        assertThat(allAfter.count() - allBefore.count())
                .as("**何件か**が一覧の上に出る").isEqualTo(3);
        assertThat(allAfter.total() - allBefore.total())
                .as("**いくらか**が一覧の上に出る").isEqualTo(8800);

        締め confirmedAfter = 締めを読む("CONFIRMED");
        assertThat(confirmedAfter.count() - confirmedBefore.count())
                .as("**絞り込んだ結果の件数である**").isEqualTo(2);
        assertThat(confirmedAfter.total() - confirmedBefore.total())
                .as("**絞り込んだ結果の合計である。** 全件の合計を出すと元帳と合わない")
                .isEqualTo(3300);
    }

    /**
     * <strong>発行日の期間で締められる</strong>（IT14 レビュー C1）。
     *
     * <p>月次の締めは「<strong>先月に発行した分</strong>」を数える作業である。
     * 期間で切れないと、経理担当者は全件を目で追って先月分を拾うことになる。
     *
     * <p><strong>末尾の日を含める。</strong>「4 月 30 日まで」と指定して
     * 4 月 30 日発行分が落ちたら、締めの金額が足りない。
     *
     * <p><strong>業務のタイムゾーンで切る。</strong> DB のタイムゾーン（CI は UTC）で
     * 切ると、時差の分だけ月初・月末の請求書が隣の月に落ちる。
     * 9 時前に発行した請求書は、UTC では前日になる。
     */
    @Test
    void 発行日の期間で締められる() throws Exception {
        String withinMonth = 発行済みの請求書("2026-04-30 08:00:00+09", "期間テスト商事");
        String nextMonth = 発行済みの請求書("2026-05-01 08:00:00+09", "期間テスト商事");

        String html = 請求書一覧("", "2026-04-01", "2026-04-30", "期間テスト商事");

        assertThat(html)
                .as("**末尾の日を含める。** 9 時前の発行が UTC で前日に落ちてもいけない")
                .contains(withinMonth);
        assertThat(html)
                .as("翌月に発行した請求書は先月の締めに含まれない")
                .doesNotContain(nextMonth);
    }

    /**
     * <strong>荷主で絞れる</strong>（IT14 レビュー C1）。
     *
     * <p>「この会社の先月の請求はいくらか」に答えられないと、
     * 荷主からの問い合わせに全件を目で追って答えることになる。
     *
     * <p><strong>凍結した宛名で探す</strong>（C7）。荷主が改名しても、
     * 発行済みの請求書は発行時点の名前で見つかる。
     */
    @Test
    void 荷主で絞れる() throws Exception {
        String targeted = 発行済みの請求書("2026-06-10 10:00:00+09", "甲野運送");
        String other = 発行済みの請求書("2026-06-10 10:00:00+09", "乙川物流");

        String html = 請求書一覧("", null, null, "甲野");

        assertThat(html).contains(targeted);
        assertThat(html)
                .as("**絞り込みは SQL で行う。** 読み出してから捨てると件数と合計がずれる")
                .doesNotContain(other);
    }

    /**
     * <strong>確定したまま発行していない請求書を見つけられる</strong>（IT14 レビュー C2）。
     *
     * <p><strong>どちらの軸でも選び出せない。</strong> 料金の状態は「確定」であり、
     * 支払いの状態はまだ始まっていない。<strong>確定で止まった請求書は
     * 誰も請求しないまま月をまたぐ。</strong>
     */
    @Test
    void 確定したまま未発行の請求書を絞り込める() throws Exception {
        UUID unissued = 引取済みの貨物("TRK-20260601-5031");
        請求書を作る(unissued, "CONFIRMED", 3300);
        String issued = 発行済みの請求書("2026-06-20 10:00:00+09", "発行済み商事");

        String html = 請求書一覧("AWAITING_ISSUE", null, null, null);

        assertThat(html)
                .as("確定したのに発行していない請求書が並ぶ")
                .contains("TRK-CLOSING");
        assertThat(html)
                .as("**発行済みは発行待ちではない**")
                .doesNotContain(issued);
    }

    /**
     * <strong>ダッシュボードの「発行待ち」から、その一覧へ行ける</strong>（C2）。
     *
     * <p><strong>件数を出すだけでは仕事は進まない</strong>（IT9 のふりかえり T2）。
     * <strong>数えた対象にそのまま行けること</strong>まで確かめる。
     */
    @Test
    void 発行待ちのカードから発行待ちの一覧へ行ける() throws Exception {
        請求書を作る(引取済みの貨物("TRK-20260601-5032"), "CONFIRMED", 4400);

        String html = mockMvc.perform(get("/")
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("発行待ち");
        assertThat(html)
                .as("**行き先が数えた対象と一致する**（C33 の型）")
                .contains("/billing/invoices?status=AWAITING_ISSUE");
    }

    /** 発行済みの請求書を 1 件作り、その請求番号を返す。 */
    private String 発行済みの請求書(String issuedAt, String shipperName) {
        UUID bookingId = 引取済みの貨物("TRK-%s".formatted(UUID.randomUUID().toString()
                .substring(0, 12)));
        jdbcTemplate.update("""
                INSERT INTO invoice (
                    invoice_number, booking_id, shipper_id,
                    shipper_name, tracking_number,
                    base_amount_value, base_amount_currency,
                    discount_rate, tax_rate, tax_amount_value, tax_amount_currency,
                    total_amount_value, total_amount_currency,
                    charge_status, payment_status, issued_at, due_date, version)
                VALUES ('INV-' || LPAD(nextval('invoice_number_seq')::text, 8, '0'),
                        ?, ?, ?, 'TRK-ISSUED', 1000, 'JPY',
                        0, 0.1000, 100, 'JPY', 1100, 'JPY', 'CONFIRMED', 'PENDING',
                        CAST(? AS TIMESTAMP WITH TIME ZONE), DATE '2099-12-31', 0)
                """, bookingId, UUID.randomUUID(), shipperName, issuedAt);
        return jdbcTemplate.queryForObject(
                "SELECT invoice_number FROM invoice WHERE booking_id = ?",
                String.class, bookingId);
    }

    /** 一覧の上に出ている締め。 */
    private record 締め(int count, int total) {
    }

    private 締め 締めを読む(String status) throws Exception {
        String html = 請求書一覧(status);
        return new 締め(数を読む(html, "<span class=\"badge text-bg-secondary\">"),
                数を読む(html, "<strong>"));
    }

    /** 画面に出ている数字を読む（カンマと単位を落とす）。 */
    private static int 数を読む(String html, String marker) {
        int from = html.indexOf(marker);
        assertThat(from).as("**締めが画面に出ていない**（%s）", marker).isNotNegative();
        String text = html.substring(from + marker.length());
        text = text.substring(0, text.indexOf('<')).replace(",", "")
                .replace("件", "").replace("円", "").strip();
        return Integer.parseInt(text);
    }

    private String 請求書一覧(String status) throws Exception {
        return 請求書一覧(status == null ? "" : status, null, null, null);
    }

    private String 請求書一覧(String status, String from, String to, String shipper)
            throws Exception {
        var request = get("/billing/invoices")
                .param("status", status == null ? "" : status)
                .with(user("billing1").roles("BILLING"));
        if (from != null) {
            request = request.param("issuedFrom", from);
        }
        if (to != null) {
            request = request.param("issuedTo", to);
        }
        if (shipper != null) {
            request = request.param("shipper", shipper);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 請求書を 1 件作る（一覧の行を用意するため）。 */
    private void 請求書を作る(UUID bookingId, String chargeStatus, int total) {
        jdbcTemplate.update("""
                INSERT INTO invoice (
                    invoice_number, booking_id, shipper_id,
                    shipper_name, tracking_number,
                    base_amount_value, base_amount_currency,
                    discount_rate, tax_rate, tax_amount_value, tax_amount_currency,
                    total_amount_value, total_amount_currency,
                    charge_status, payment_status, version)
                VALUES ('INV-' || LPAD(nextval('invoice_number_seq')::text, 8, '0'),
                        ?, ?, '締めテスト商事', 'TRK-CLOSING', ?, 'JPY',
                        0, 0.1000, 0, 'JPY', ?, 'JPY', ?, 'PENDING', 0)
                """, bookingId, UUID.randomUUID(), total, total, chargeStatus);
    }

    private String 請求対象一覧() throws Exception {
        return mockMvc.perform(get("/billing/pending").with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 引取まで済んだ貨物を用意する（引取日は書かない）。 */
    private UUID 引取済みの貨物(String trackingNumber) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("締めテスト商事")
                .status("DELIVERED", "ROUTED")
                .trackingNumber(trackingNumber)
                .insert();
        UUID bookingId = cargo.bookingId();
        long cargoId = cargo.cargoId();

        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, 'V0001', 'JPOSA', 'USLAX',
                        TIMESTAMP WITH TIME ZONE '2026-04-02 09:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-04-20 09:00:00+09', 1)
                """, cargoId);
        return bookingId;
    }
}
