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
 * 精算を処理する（US23）。<strong>画面から一続きに踏む。</strong>
 *
 * <p>集約の単体テストは「画面でどう見えるか」を判別しない。
 * 発行・入金確認は<strong>予約の状態にまで届く操作</strong>であり、
 * 経理担当者の画面から予約が精算済みになるところまでを 1 本で確かめる。
 *
 * <table>
 *   <caption>受入基準とテストの対応</caption>
 *   <tr><td>「確定」状態の輸送料金をもとに精算書を発行できる</td>
 *       <td>{@link #確定から発行して入金を確認すると予約が精算済みになる()}／
 *           拒む側: {@link #下書きの請求書は画面からも発行できない()}</td></tr>
 *   <tr><td>精算書に請求番号・請求金額・支払い期限が含まれる</td>
 *       <td>{@link #確定から発行して入金を確認すると予約が精算済みになる()}</td></tr>
 *   <tr><td>精算書が荷主にメール通知される</td>
 *       <td>{@link #発行すると荷主への通知が記録される()}</td></tr>
 *   <tr><td>入金確認後、精算状態と予約状態が「精算済」になる</td>
 *       <td>{@link #確定から発行して入金を確認すると予約が精算済みになる()}</td></tr>
 *   <tr><td>支払い期限超過時、経理担当者が気づける</td>
 *       <td>{@link #期限を過ぎた請求書が督促の一覧とカードに出る()}</td></tr>
 * </table>
 */
@AutoConfigureMockMvc
@DisplayName("精算を処理する（US23）")
class SettlementScenarioTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * <strong>確定 → 発行 → 入金確認 → 予約が精算済み</strong>（US23 の受入基準 1・2・4）。
     *
     * <p><strong>予約の状態まで見る。</strong> 請求書の中だけで完結すると、
     * US36 の「精算済みには訂正・取り消しできない」が効き始めない。
     */
    @Test
    void 確定から発行して入金を確認すると予約が精算済みになる() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260701-7001");
        String invoiceNumber = 料金を算出して確定する(bookingId);

        発行する(invoiceNumber)
                .andExpect(flash().attributeExists("flashSuccess"));

        String issued = 請求書詳細(invoiceNumber);
        assertThat(issued)
                .as("**請求番号・請求金額・支払期限**が精算書に出る（受入基準 2）")
                .contains(invoiceNumber)
                .contains("1,100 円")
                .contains("支払期限")
                .contains("未入金");

        入金を確認する(invoiceNumber, "1100")
                .andExpect(flash().attributeExists("flashSuccess"));

        assertThat(請求書詳細(invoiceNumber)).contains("入金確認済");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT booking_status FROM cargo WHERE booking_id = ?",
                String.class, bookingId))
                .as("**予約状態も精算済になる**（受入基準 4。遷移表 #8）")
                .isEqualTo("SETTLED");
    }

    /**
     * <strong>下書きの請求書は画面からも発行できない</strong>（受入基準 1）。
     *
     * <p>ドメインが拒むことは単体で確かめている。<strong>画面から送った道で
     * 500 にならず、理由が読める</strong>ことをここで見る。
     */
    @Test
    void 下書きの請求書は画面からも発行できない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260701-7002");
        String invoiceNumber = 料金を算出する(bookingId);

        発行する(invoiceNumber)
                .andExpect(flash().attribute("flashError",
                        "確定していない請求書は発行できません"));

        assertThat(請求書詳細(invoiceNumber))
                .as("発行されていない")
                .doesNotContain("支払期限");
    }

    /**
     * <strong>請求額と違う入金は画面からも通らない</strong>（設計判断）。
     *
     * <p>差額の扱いは業務である。<strong>黙って受けると帳簿と合わなくなる。</strong>
     */
    @Test
    void 請求額と違う入金は画面からも通らない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260701-7003");
        String invoiceNumber = 料金を算出して確定する(bookingId);
        発行する(invoiceNumber);

        入金を確認する(invoiceNumber, "1000")
                .andExpect(flash().attributeExists("flashError"));

        assertThat(請求書詳細(invoiceNumber))
                .as("一部入金を受けていない")
                .contains("未入金");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT booking_status FROM cargo WHERE booking_id = ?",
                String.class, bookingId))
                .as("**予約も動かない。** 入金していない貨物が精算済みになってはならない")
                .isEqualTo("DELIVERED");
    }

    /** <strong>発行すると荷主への通知が記録される</strong>（受入基準 3。ADR-006）。 */
    @Test
    void 発行すると荷主への通知が記録される() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260701-7004");
        String invoiceNumber = 料金を算出して確定する(bookingId);

        発行する(invoiceNumber);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT content FROM booking_notification
                 WHERE booking_id = ? AND notification_type = 'INVOICE_ISSUED'
                """, String.class, bookingId))
                .as("**何を伝えたかが読める。** 「送った」だけの記録では"
                        + "「いくらの請求書か」と問われて答えられない")
                .contains(invoiceNumber)
                .contains("1100");
    }

    /**
     * <strong>期限を過ぎた請求書が督促の一覧とカードに出る</strong>（受入基準 5）。
     *
     * <p><strong>気づく手段は次の行動へ繋ぐ。</strong> 件数だけ出しても、
     * そこから対象へ行けなければ督促は進まない。
     */
    @Test
    void 期限を過ぎた請求書が督促の一覧とカードに出る() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260701-7005");
        String invoiceNumber = 料金を算出して確定する(bookingId);
        発行する(invoiceNumber);
        // 期限を過去にする（**時計を進める代わりに期限を戻す**）
        jdbcTemplate.update(
                "UPDATE invoice SET due_date = CURRENT_DATE - 3 WHERE invoice_number = ?",
                invoiceNumber);

        String overdue = mockMvc.perform(get("/billing/invoices")
                        .param("status", "OVERDUE")
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(overdue)
                .as("督促の対象として並ぶ")
                .contains(invoiceNumber)
                .contains("支払期限超過");

        String dashboard = mockMvc.perform(get("/")
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(dashboard)
                .as("**気づく手段が次の行動へ繋がっている**（カードから一覧へ行ける）")
                .contains("支払期限超過")
                .contains("/billing/invoices?status=OVERDUE");
    }

    /**
     * <strong>期限当日は督促の対象にしない</strong>（受入基準 5）。
     *
     * <p>これが無いと、期限を過ぎているかを日付で見ずに
     * 「期限がある＝督促」と実装しても緑になる。
     */
    @Test
    void 期限当日は督促の対象にしない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260701-7006");
        String invoiceNumber = 料金を算出して確定する(bookingId);
        発行する(invoiceNumber);
        jdbcTemplate.update(
                "UPDATE invoice SET due_date = CURRENT_DATE WHERE invoice_number = ?",
                invoiceNumber);

        String overdue = mockMvc.perform(get("/billing/invoices")
                        .param("status", "OVERDUE")
                        .with(user("billing1").roles("BILLING")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(overdue)
                .as("**約束を守っている相手に催促しない**")
                .doesNotContain(invoiceNumber);
    }

    /** <strong>経理担当者以外は発行も入金確認もできない。</strong> */
    @Test
    void 経理担当者以外は精算を操作できない() throws Exception {
        UUID bookingId = 引取済みの貨物("TRK-20260701-7007");
        String invoiceNumber = 料金を算出して確定する(bookingId);

        mockMvc.perform(post("/billing/invoices/{n}/issuance", invoiceNumber)
                        .with(user("sales1").roles("SALES")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/billing/invoices/{n}/payment", invoiceNumber)
                        .param("paidAmount", "1100")
                        .with(user("shipper1").roles("SHIPPER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions 発行する(String invoiceNumber)
            throws Exception {
        return mockMvc.perform(post("/billing/invoices/{n}/issuance", invoiceNumber)
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private org.springframework.test.web.servlet.ResultActions 入金を確認する(
            String invoiceNumber, String amount) throws Exception {
        return mockMvc.perform(post("/billing/invoices/{n}/payment", invoiceNumber)
                        .param("paidAmount", amount)
                        .param("method", "BANK_TRANSFER")
                        .param("reference", "TX-0001")
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private String 請求書詳細(String invoiceNumber) throws Exception {
        return mockMvc.perform(get("/billing/invoices/{n}", invoiceNumber)
                        .with(user("billing1").roles("BILLING")))
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

    private String 料金を算出して確定する(UUID bookingId) throws Exception {
        String invoiceNumber = 料金を算出する(bookingId);
        mockMvc.perform(post("/billing/invoices/{n}/confirmation", invoiceNumber)
                        .with(user("billing1").roles("BILLING")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        return invoiceNumber;
    }

    /** 引取まで済んだ貨物を用意する（基本料金 1,000 円・税込 1,100 円）。 */
    private UUID 引取済みの貨物(String trackingNumber) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street, discount_rate)
                VALUES (?, ?, 'INDIVIDUAL', '精算テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1', 0)
                """, shipperId, "SHP-%06d".formatted(seq),
                "settlement-%d@example.com".formatted(seq));

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
}
