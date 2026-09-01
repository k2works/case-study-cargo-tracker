package com.example.billingms.interfaces.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.billingms.application.internal.commandservices.CalculateChargeUseCase;
import com.example.billingms.application.internal.queryservices.InvoiceSearchResult;
import com.example.billingms.application.internal.queryservices.SearchInvoiceUseCase;
import com.example.billingms.application.internal.commandservices.SettleInvoiceUseCase;
import com.example.billingms.domain.model.valueobjects.Money;
import com.example.billingms.domain.repository.InvoiceRepository;
import com.example.shared.auth.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 請求書を探す API（US38）。
 *
 * <p><strong>月末の締めが表計算に落ちたまま</strong>だった——IT11・IT12 のレビューで
 * 経理担当者から 2 IT 連続の指摘を受け、IT13・IT15 では計画に入らなかった 4 度目の
 * 申し送りである。
 *
 * <p>{@code BillingControllerTest} から分けたのは行数の都合ではなく
 * <strong>変わる理由が違う</strong>ためである——ここが変わるのは、締めの探し方が
 * 変わるときである。
 */
@WebMvcTest(BillingController.class)
@DisplayName("請求書の検索 API")
class InvoiceSearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalculateChargeUseCase calculateCharge;

    @MockitoBean
    private InvoiceRepository invoices;

    @MockitoBean
    private SettleInvoiceUseCase settlement;

    @MockitoBean
    private SearchInvoiceUseCase searchInvoices;

    private static MockHttpServletRequestBuilder asAccountant(
            MockHttpServletRequestBuilder request) {
        return request.header(AuthenticatedUser.USER_ID_HEADER, "accountant01")
                .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ACCOUNTANT");
    }

    /**
     * <strong>読めない発行月は断る。</strong>黙って「指定なし」に倒すと、打ち間違えた
     * 担当者には全件が返り、絞ったつもりの数字を締めに使うことになる。
     */
    @Test
    @DisplayName("発行月の形式が不正なら 400 を返す")
    void rejectsMalformedIssuedMonth() throws Exception {
        mockMvc.perform(asAccountant(
                        get("/api/v1/billing/invoices").param("issuedMonth", "2026-13")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    /**
     * <strong>切ったことを黙らない。</strong>件数を知らせずに切ると、担当者は
     * 「一覧に出ていないから無い」と読む（予約一覧・通関一覧と同じ形）。
     */
    @Test
    @DisplayName("上限で切ったことを応答で知らせる")
    void tellsWhenTheListWasTruncated() throws Exception {
        when(searchInvoices.search(ArgumentMatchers.any())).thenReturn(
                new InvoiceSearchResult(List.of(), 500L, Money.yen(new BigDecimal("1000"))));

        mockMvc.perform(asAccountant(get("/api/v1/billing/invoices")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(500))
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.limit").value(SearchInvoiceUseCase.SEARCH_LIMIT));
    }

    @Test
    @DisplayName("条件を指定しなければ、これまでどおりの一覧である")
    void listsWithoutCriteria() throws Exception {
        when(searchInvoices.search(ArgumentMatchers.any())).thenReturn(
                new InvoiceSearchResult(List.of(), 0L, Money.zero()));

        mockMvc.perform(asAccountant(get("/api/v1/billing/invoices")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoices").isArray())
                .andExpect(jsonPath("$.truncated").value(false));
    }

    /**
     * <strong>件数と合計を一緒に返す。</strong>月末の締めで要るのは「その月に出した
     * 請求書の合計」であり、画面で足し上げると上限で切った瞬間に「見えている分だけの
     * 合計」に化ける。
     */
    @Test
    @DisplayName("発行済みの精算書を、件数と合計とともに並べる")
    void listsInvoicesWithCountAndTotal() throws Exception {
        when(searchInvoices.search(ArgumentMatchers.any())).thenReturn(
                new InvoiceSearchResult(List.of(), 3L, Money.yen(new BigDecimal("46200"))));

        mockMvc.perform(asAccountant(get("/api/v1/billing/invoices")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.totalAmount").value(46200))
                .andExpect(jsonPath("$.currency").value("JPY"));
    }
}
