package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        請求書を作る(引取済みの貨物("TRK-20260601-5021"), "CONFIRMED", 1100);
        請求書を作る(引取済みの貨物("TRK-20260601-5022"), "CONFIRMED", 2200);
        請求書を作る(引取済みの貨物("TRK-20260601-5023"), "DRAFT", 5500);

        assertThat(請求書一覧(null))
                .as("**何件でいくらか**が一覧の上に出る")
                .contains("3 件")
                .contains("8,800 円");

        assertThat(請求書一覧("CONFIRMED"))
                .as("**絞り込んだ結果の合計である。** 全件の合計を出すと元帳と合わない")
                .contains("2 件")
                .contains("3,300 円");
    }

    private String 請求書一覧(String status) throws Exception {
        return mockMvc.perform(get("/billing/invoices")
                        .param("status", status == null ? "" : status)
                        .with(user("billing1").roles("BILLING")))
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
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street, discount_rate)
                VALUES (?, ?, 'INDIVIDUAL', '締めテスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1', 0)
                """, shipperId, "SHP-%06d".formatted(seq),
                "closing-%d@example.com".formatted(seq));

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
        return bookingId;
    }
}
