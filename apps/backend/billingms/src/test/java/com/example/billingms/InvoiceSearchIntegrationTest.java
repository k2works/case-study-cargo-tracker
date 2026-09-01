package com.example.billingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.billingms.application.internal.queryservices.InvoiceSearchResult;
import com.example.billingms.domain.model.valueobjects.InvoiceSearchCriteria;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 請求書を探し、その月の合計を読む（US38）。
 *
 * <p><strong>4 度目の申し送りである。</strong>月末の締めが表計算に落ちたまま、
 * IT11・IT12 のレビューで 2 IT 連続の指摘を受け、IT13・IT15 では計画に入らなかった。
 *
 * <p><strong>合計は締めの数字としてそのまま使われる。</strong>赤伝を含めた合計を
 * 出すと、誤りに気づく手段が無いまま経理の判断に入る。
 */
@DisplayName("請求書の検索")
class InvoiceSearchIntegrationTest extends BillingIntegrationTestBase {

    @Autowired
    private com.example.billingms.domain.repository.InvoiceRepository invoices;

    @Autowired
    private com.example.billingms.domain.repository.InvoiceNumbering numbering;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 実業務の請求書を 1 通発行し、その請求番号を返す。 */
    private String issueInvoiceFor(String shipperName, String bookingId) {
        return issue(shipperName, bookingId, false);
    }

    /** シミュレーション由来の請求書を 1 通。 */
    private String issueSimulatedInvoiceFor(String shipperName, String bookingId) {
        return issue(shipperName, bookingId, true);
    }

    private String issue(String shipperName, String bookingId, boolean simulated) {
        com.example.billingms.domain.model.aggregates.Invoice invoice =
                com.example.billingms.domain.model.aggregates.Invoice.issue(
                        new com.example.billingms.domain.model.valueobjects.InvoiceHeader(
                                numbering.next(),
                                com.example.billingms.domain.model.valueobjects.BillingBookingId
                                        .of(bookingId),
                                com.example.billingms.domain.model.valueobjects.BillingShipperId
                                        .corporate("1", shipperName),
                                java.time.Instant.now(), simulated),
                        new com.example.billingms.domain.model.valueobjects.InvoiceCharges(
                                com.example.billingms.domain.model.valueobjects.TransportCharge.of(
                                        ChargeFixtures.domesticLegs(2),
                                        new java.math.BigDecimal("4200"),
                                        com.example.billingms.domain.model.valueobjects.CargoType
                                                .GENERAL),
                                com.example.billingms.domain.model.valueobjects.DiscountPolicy
                                        .none(),
                                null,
                                com.example.billingms.domain.model.valueobjects.TaxRate.standard()),
                        java.util.List.of(), BUSINESS_ZONE);
        invoices.save(invoice);
        return invoice.invoiceId().value();
    }

    private void voidInvoice(String invoiceNumber) {
        jdbcTemplate.update("UPDATE invoice SET voided_at = NOW(), void_marker = invoice_number,"
                + " void_reason = '検査' WHERE invoice_number = ?", invoiceNumber);
    }

    /** 業務の今日。**テストも同じ暦で決める**（CI の UTC で落ちるテストを作らない）。 */
    private java.time.LocalDate businessToday() {
        return java.time.LocalDate.now(BUSINESS_ZONE);
    }


    @Autowired
    private com.example.billingms.application.internal.queryservices.SearchInvoiceUseCase search;

    @Test
    @DisplayName("荷主名の一部で探せる")
    void findsByPartOfTheShipperName() {
        String number = issueInvoiceFor("検索商事", "SEARCH-0001");

        InvoiceSearchResult result = search.search(InvoiceSearchCriteria.of("検索商", null));

        assertThat(result.invoices())
                .extracting(invoice -> invoice.invoiceId().value())
                .contains(number);
    }

    @Test
    @DisplayName("請求番号でも予約番号でも探せる")
    void findsByInvoiceNumberOrBookingId() {
        String number = issueInvoiceFor("番号商事", "SEARCH-0002");

        assertThat(search.search(InvoiceSearchCriteria.of(number, null)).invoices())
                .as("請求番号で引けない")
                .isNotEmpty();
        assertThat(search.search(InvoiceSearchCriteria.of("SEARCH-0002", null)).invoices())
                .as("予約番号で引けない")
                .isNotEmpty();
    }

    @Test
    @DisplayName("発行月で絞り込める")
    void filtersByIssuedMonth() {
        issueInvoiceFor("当月商事", "SEARCH-0003");

        YearMonth issued = YearMonth.from(businessToday());
        assertThat(search.search(InvoiceSearchCriteria.of("当月商", issued)).invoices())
                .as("当月で絞ったのに出ない")
                .isNotEmpty();
        assertThat(search.search(InvoiceSearchCriteria.of("当月商", issued.minusMonths(1)))
                        .invoices())
                .as("別の月で絞ったのに出ている")
                .isEmpty();
    }

    /**
     * <strong>件数と合計は、一覧と同じ条件で数える。</strong>別々に組み立てると
     * 「12 件あります」と出るのに開くと 3 件、という形になる。
     */
    @Test
    @DisplayName("件数は一覧の中身と一致する")
    void countMatchesTheListedInvoices() {
        issueInvoiceFor("件数商事", "SEARCH-0004");
        issueInvoiceFor("件数商事", "SEARCH-0005");

        InvoiceSearchResult result = search.search(InvoiceSearchCriteria.of("件数商事", null));

        assertThat(result.count()).isEqualTo(result.invoices().size());
    }

    /**
     * <strong>取り消し済みは合計に入れない。</strong>合計は締めの数字として
     * そのまま使われる——赤伝を含めると、誤りに気づく手段が無いまま判断に入る。
     */
    @Test
    @DisplayName("取り消した請求書は、合計にも件数にも入らない")
    void excludesVoidedFromTheTotal() {
        String kept = issueInvoiceFor("赤伝商事", "SEARCH-0006");
        String voided = issueInvoiceFor("赤伝商事", "SEARCH-0007");
        voidInvoice(voided);

        InvoiceSearchResult result = search.search(InvoiceSearchCriteria.of("赤伝商事", null));

        assertThat(result.invoices())
                .extracting(invoice -> invoice.invoiceId().value())
                .as("取り消した請求書が一覧に残っている")
                .contains(kept)
                .doesNotContain(voided);
        assertThat(result.total().amount())
                .as("取り消した分が合計に入っている")
                .isEqualByComparingTo(amountOf(kept));
    }

    /** <strong>シミュレーション由来は出ない</strong>（[ADR-030] 決定 3）。 */
    @Test
    @DisplayName("シミュレーション由来の請求書は、検索結果に出ない")
    void keepsSimulatedOut() {
        String real = issueInvoiceFor("由来商事", "SEARCH-0008");
        String simulated = issueSimulatedInvoiceFor("由来商事", "SEARCH-0009");

        assertThat(search.search(InvoiceSearchCriteria.of("由来商事", null)).invoices())
                .extracting(invoice -> invoice.invoiceId().value())
                .contains(real)
                .doesNotContain(simulated);
    }

    @Test
    @DisplayName("条件を組み合わせられる")
    void combinesConditions() {
        issueInvoiceFor("組合せ商事", "SEARCH-0010");

        assertThat(search.search(InvoiceSearchCriteria.of("組合せ商事",
                        YearMonth.from(businessToday()))).invoices())
                .isNotEmpty();
    }

    private BigDecimal amountOf(String invoiceNumber) {
        return search.search(InvoiceSearchCriteria.of(invoiceNumber, null)).total().amount();
    }
}
