package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 督促の導線と記録（IT14 レビュー C3）。
 *
 * <p>US23 で「支払期限を過ぎた請求書に気づく」ところまでは作った。
 * <strong>そこから先が無かった。</strong> 経理担当者は請求書の画面を閉じ、
 * 荷主一覧を開き、名前で探し直すことになる。連絡したことも残らないため、
 * <strong>二重に催促するか、逆に誰も連絡しないまま月をまたぐ</strong>。
 *
 * <p>「気づく手段は次の行動へ繋ぐ」の型である。
 */
@AutoConfigureMockMvc
@DisplayName("督促の導線と記録（C3）")
class InvoiceReminderTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * <strong>請求書の画面から荷主へ連絡できる</strong>（C3）。
     *
     * <p><strong>連絡先は写し取らない。</strong> 宛名は発行時点の事実として凍結するが、
     * 連絡先は「いま届く先」である。写すと、荷主が電話番号を変えた日から
     * 古い番号にかけ続けることになる。
     */
    @Test
    void 未入金の請求書から荷主の連絡先へ行ける() throws Exception {
        String invoiceNumber = 発行済みの請求書("reminder-a@example.com", "06-1111-2222");

        String html = 請求書詳細(invoiceNumber);

        assertThat(html)
                .as("**そのまま連絡できる**（画面を閉じて荷主一覧を開き直さない）")
                .contains("mailto:reminder-a@example.com")
                .contains("tel:06-1111-2222");
    }

    /**
     * <strong>荷主が改名しても、いまの連絡先で連絡できる</strong>（C3）。
     *
     * <p><strong>宛名（凍結）と連絡先（いま）は目的が違う。</strong>
     * 画面はその違いを説明する。
     */
    @Test
    void 改名した荷主にもいまの連絡先が出る() throws Exception {
        String invoiceNumber = 発行済みの請求書("reminder-b@example.com", "06-3333-4444");
        jdbcTemplate.update("""
                UPDATE shipper SET name = '改名後ロジスティクス'
                 WHERE id = (SELECT shipper_id FROM invoice WHERE invoice_number = ?)
                """, invoiceNumber);

        String html = 請求書詳細(invoiceNumber);

        assertThat(html)
                .as("いま届く先の名前が出る")
                .contains("改名後ロジスティクス");
        assertThat(html)
                .as("**発行時点の宛名も残る**（請求書に書いた名前である）")
                .contains("督促テスト商事");
    }

    /**
     * <strong>督促したことが残る</strong>（C3）。
     *
     * <p><strong>催促そのものは人が行う</strong>（ADR-006）。残すのは
     * いつ・誰が・何を伝えたかであり、次に開いた人が同じ相手へ
     * もう一度かけずに済む。
     */
    @Test
    void 督促したことを記録して読み返せる() throws Exception {
        String invoiceNumber = 発行済みの請求書("reminder-c@example.com", "06-5555-6666");

        mockMvc.perform(post("/billing/invoices/{n}/reminders", invoiceNumber)
                        .param("note", "電話で入金予定日を確認")
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        String html = 請求書詳細(invoiceNumber);
        assertThat(html)
                .contains("電話で入金予定日を確認")
                .contains("billing1");
    }

    /**
     * <strong>伝えた内容が無い督促も記録できる</strong>（C3）。
     *
     * <p>電話で伝えたことだけが事実の場合がある。<strong>内容を必須にすると、
     * 記録そのものを飛ばすようになる</strong>。
     */
    @Test
    void 伝えた内容が無くても督促を記録できる() throws Exception {
        String invoiceNumber = 発行済みの請求書("reminder-d@example.com", "06-7777-8888");

        mockMvc.perform(post("/billing/invoices/{n}/reminders", invoiceNumber)
                        .with(user("billing2").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(請求書詳細(invoiceNumber))
                .as("**いつ・誰が**は残る。それが記録の本体である")
                .contains("billing2");
    }

    /**
     * <strong>入金が済んだ請求書に督促の入口を出さない</strong>（C3）。
     *
     * <p>置いたままにすると、<strong>払ってくれた相手に催促する</strong>。
     */
    @Test
    void 入金済みの請求書には督促の入口が出ない() throws Exception {
        String invoiceNumber = 発行済みの請求書("reminder-e@example.com", "06-9999-0000");
        jdbcTemplate.update(
                "UPDATE invoice SET payment_status = 'CONFIRMED' WHERE invoice_number = ?",
                invoiceNumber);
        // **入金の記録まで入れる。** 状態だけ動かすのは画面が見ている事実と違う
        jdbcTemplate.update("""
                INSERT INTO payment (
                    invoice_id, paid_amount_value, paid_amount_currency,
                    paid_at, payment_method, transaction_reference)
                SELECT i.id, 1100, 'JPY', CURRENT_TIMESTAMP, 'BANK_TRANSFER', 'TX-REMIND'
                  FROM invoice i WHERE i.invoice_number = ?
                """, invoiceNumber);

        assertThat(請求書詳細(invoiceNumber))
                .as("**払った相手に催促しない**")
                .doesNotContain("督促したことを記録する");
    }

    private String 請求書詳細(String invoiceNumber) throws Exception {
        return mockMvc.perform(get("/billing/invoices/{n}", invoiceNumber)
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** 発行済み・未入金の請求書を 1 件作り、その請求番号を返す。 */
    private String 発行済みの請求書(String email, String phone) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street, discount_rate)
                VALUES (?, ?, 'INDIVIDUAL', '督促テスト商事', ?, ?,
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1', 0)
                """, shipperId, "SHP-%06d".formatted(seq), email, phone);

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number)
                VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'USLAX', CURRENT_DATE + 60,
                        'DELIVERED', 'ROUTED', ?)
                """, bookingId, shipperId, "TRK-%s".formatted(seq));

        jdbcTemplate.update("""
                INSERT INTO invoice (
                    invoice_number, booking_id, shipper_id,
                    shipper_name, tracking_number,
                    base_amount_value, base_amount_currency,
                    discount_rate, tax_rate, tax_amount_value, tax_amount_currency,
                    total_amount_value, total_amount_currency,
                    charge_status, payment_status, issued_at, due_date, version)
                VALUES ('INV-' || LPAD(nextval('invoice_number_seq')::text, 8, '0'),
                        ?, ?, '督促テスト商事', ?, 1000, 'JPY',
                        0, 0.1000, 100, 'JPY', 1100, 'JPY', 'CONFIRMED', 'PENDING',
                        CURRENT_TIMESTAMP, DATE '2099-12-31', 0)
                """, bookingId, shipperId, "TRK-%s".formatted(seq));

        return jdbcTemplate.queryForObject(
                "SELECT invoice_number FROM invoice WHERE booking_id = ?",
                String.class, bookingId);
    }
}
