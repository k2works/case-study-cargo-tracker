package com.example.billingms.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.billingms.application.internal.AlreadyInvoicedException;
import com.example.billingms.application.internal.BillingNotAvailableException;
import com.example.billingms.application.internal.CalculateChargeUseCase;
import com.example.billingms.application.internal.ChargeCalculation;
import com.example.billingms.application.port.BillableCargoSnapshot;
import com.example.billingms.application.port.InvoiceRepository;
import com.example.billingms.domain.model.BillingBookingId;
import com.example.billingms.domain.model.BillingShipperId;
import com.example.billingms.domain.model.CancellationFee;
import com.example.billingms.domain.model.CancelledAtStatus;
import com.example.billingms.domain.model.CargoType;
import com.example.billingms.domain.model.DiscountPolicy;
import com.example.billingms.domain.model.DiscountRate;
import com.example.billingms.domain.model.Invoice;
import com.example.billingms.domain.model.InvoiceId;
import com.example.billingms.domain.model.InvoiceCharges;
import com.example.billingms.domain.model.InvoiceLineItem;
import com.example.billingms.domain.model.Money;
import com.example.billingms.domain.model.TaxRate;
import com.example.billingms.domain.model.TransportCharge;
import com.example.shared.auth.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 精算の API（US21・US22）。
 *
 * <p><strong>経理担当者だけが使う。</strong>請求の金額を決めるのは経理であり、営業や
 * 経路設計者とは職掌が違う。<strong>画面に出す・出さないでは守れない</strong>
 * ——URL を直接叩かれる。
 */
@WebMvcTest(BillingController.class)
@DisplayName("精算の API")
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalculateChargeUseCase calculateCharge;

    @MockitoBean
    private InvoiceRepository invoices;

    private static final TransportCharge CHARGE =
            TransportCharge.of(2, new BigDecimal("4200"), CargoType.GENERAL);

    private static ChargeCalculation corporateCalculation() {
        return new ChargeCalculation("BKG-2026000007", "丸紅商事株式会社", true, CHARGE,
                DiscountPolicy.forCorporate(DiscountRate.of(new BigDecimal("0.1000"))),
                null, null, TaxRate.standard());
    }

    private static Invoice issued() {
        return Invoice.issue(InvoiceId.of("INV-2026000001"),
                BillingBookingId.of("BKG-2026000007"),
                BillingShipperId.corporate("1", "丸紅商事株式会社"),
                InvoiceCharges.of(CHARGE, DiscountPolicy.forCorporate(
                        DiscountRate.of(new BigDecimal("0.1000"))), TaxRate.standard()),
                List.of(InvoiceLineItem.of("遅延による減額", Money.yen(new BigDecimal("-10000")))),
                Instant.parse("2027-10-01T00:00:00Z"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            asAccountant(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder
                .header(AuthenticatedUser.USER_ID_HEADER, "accountant01")
                .header(AuthenticatedUser.ROLES_HEADER, "ROLE_ACCOUNTANT");
    }

    @Nested
    @DisplayName("認可")
    class Authorization {

        /**
         * <strong>経理担当者以外は断る。</strong>
         *
         * <p>画面のルートガードだけでは守れない——URL を直接叩かれる。
         */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_SALES", "ROLE_ROUTING", "ROLE_TRACKER", "ROLE_HANDLER",
                "ROLE_SHIPPER"})
        @DisplayName("経理担当者以外は精算を扱えない")
        void rejectsEveryRoleButTheAccountant(String role) throws Exception {
            mockMvc.perform(get("/api/v1/billing/unbilled")
                            .header(AuthenticatedUser.USER_ID_HEADER, "someone")
                            .header(AuthenticatedUser.ROLES_HEADER, role))
                    .andExpect(status().isForbidden());
        }

        /**
         * <strong>権限の無い相手に入力仕様を教えない。</strong>
         *
         * <p>本体の変換は Spring がメソッド呼び出しの前に行うため、壊れた JSON は
         * 認可より先に 400 になる。<strong>それ自体は避けられない</strong>——避けるには
         * 本体を文字列で受けて自前で変換することになり、終盤で新しい方式を発明する
         * ことになる。
         *
         * <p>したがってここで確かめるのは 2 つである。
         * <ul>
         *   <li><strong>処理は走らない</strong>（権限の無い相手の要求で精算書が出ない）
         *   <li><strong>応答に入力仕様が漏れない</strong>（どの項目が何型かを教えない）
         * </ul>
         */
        @Test
        @DisplayName("権限が無ければ処理は走らず、応答に入力仕様も漏れない")
        void doesNotLeakTheRequestShapeToUnauthorizedCallers() throws Exception {
            String body = mockMvc.perform(post("/api/v1/billing/BKG-2026000007/calculate")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"adjustments\": \"これは配列ではない\"}"))
                    .andReturn().getResponse().getContentAsString();

            verify(calculateCharge, never()).confirm(anyString(), any());
            org.assertj.core.api.Assertions.assertThat(body)
                    .as("応答に入力仕様が漏れている。権限の無い相手に項目と型を教えることになる")
                    .doesNotContain("adjustments")
                    .doesNotContain("description")
                    .doesNotContain("amountValue");
        }

        /** <strong>正しい入力でも、権限が無ければ 403 である。</strong> */
        @Test
        @DisplayName("正しい入力でも、権限が無ければ 403 を返す")
        void rejectsWellFormedRequestsFromUnauthorizedCallers() throws Exception {
            mockMvc.perform(post("/api/v1/billing/BKG-2026000007/calculate")
                            .header(AuthenticatedUser.USER_ID_HEADER, "sales01")
                            .header(AuthenticatedUser.ROLES_HEADER, "ROLE_SALES")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"adjustments\": []}"))
                    .andExpect(status().isForbidden());
            verify(calculateCharge, never()).confirm(anyString(), any());
        }
    }

    @Nested
    @DisplayName("算出")
    class Calculating {

        @Test
        @DisplayName("根拠と割引を返す")
        void returnsTheBasisAndDiscount() throws Exception {
            when(calculateCharge.calculate("BKG-2026000007"))
                    .thenReturn(corporateCalculation());

            mockMvc.perform(asAccountant(get("/api/v1/billing/calculations/BKG-2026000007")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.basis.legCount").value(2))
                    .andExpect(jsonPath("$.basis.cargoTypeLabel").value("一般貨物"))
                    .andExpect(jsonPath("$.baseAmount.value").value(420000))
                    .andExpect(jsonPath("$.discountRate").value(0.1000))
                    .andExpect(jsonPath("$.discountAmount.value").value(42000))
                    .andExpect(jsonPath("$.totalAmount.value").value(415800));
        }

        /**
         * <strong>個人には割引の項目そのものを返さない</strong>（22-3）。
         *
         * <p>0 を返すと「割引が 0 だった」に読め、契約が無いことと区別できない。
         */
        @Test
        @DisplayName("個人荷主では割引の項目を返さない")
        void omitsTheDiscountForIndividuals() throws Exception {
            when(calculateCharge.calculate("BKG-2026000008")).thenReturn(
                    new ChargeCalculation("BKG-2026000008", "山田太郎", false,
                            TransportCharge.of(1, new BigDecimal("800"), CargoType.REFRIGERATED),
                            DiscountPolicy.none(), null, null, TaxRate.standard()));

            mockMvc.perform(asAccountant(get("/api/v1/billing/calculations/BKG-2026000008")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.discountRate").doesNotExist())
                    .andExpect(jsonPath("$.discountAmount").doesNotExist());
        }

        /** キャンセル料は料率の根拠つきで返す（US30-9）。 */
        @Test
        @DisplayName("キャンセル料を、料率の根拠つきで返す")
        void returnsTheCancellationFeeWithItsBasis() throws Exception {
            when(calculateCharge.calculate("BKG-2026000010")).thenReturn(
                    new ChargeCalculation("BKG-2026000010", "丸紅商事株式会社", true, CHARGE,
                            DiscountPolicy.none(), null,
                            CancellationFee.forStatus(CancelledAtStatus.IN_TRANSIT,
                                    Money.yen(new BigDecimal("420000"))),
                            TaxRate.standard()));

            mockMvc.perform(asAccountant(get("/api/v1/billing/calculations/BKG-2026000010")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancellationFee.bookingStatusLabel").value("輸送中"))
                    .andExpect(jsonPath("$.cancellationFee.feeRate").value(0.30))
                    .andExpect(jsonPath("$.cancellationFee.amount.value").value(126000));
        }

        /** 誤配の記録を根拠として返す（21-6）。 */
        @Test
        @DisplayName("誤配の記録を根拠として返す")
        void returnsTheMisrouteAsEvidence() throws Exception {
            when(calculateCharge.calculate("BKG-2026000009")).thenReturn(
                    new ChargeCalculation("BKG-2026000009", "丸紅商事株式会社", true, CHARGE,
                            DiscountPolicy.none(),
                            new BillableCargoSnapshot.Misroute(
                                    Instant.parse("2027-09-09T00:00:00Z"), "SGSIN", "Singapore"),
                            null, TaxRate.standard()));

            mockMvc.perform(asAccountant(get("/api/v1/billing/calculations/BKG-2026000009")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.misroute.locationName").value("Singapore"));
        }

        /** **引取が終わっていない予約は 409。** URL を直接開かれても守る（決定 5）。 */
        @Test
        @DisplayName("引取が終わっていない予約は 409 を返す")
        void rejectsCargoThatCannotBeBilled() throws Exception {
            when(calculateCharge.calculate("BKG-2026000001"))
                    .thenThrow(new BillingNotAvailableException("引取が終わっていない予約です"));

            mockMvc.perform(asAccountant(get("/api/v1/billing/calculations/BKG-2026000001")))
                    .andExpect(status().isConflict());
        }

        /** **二重請求を断る**（決定 4）。 */
        @Test
        @DisplayName("すでに発行されている予約は 409 を返す")
        void rejectsCargoThatIsAlreadyInvoiced() throws Exception {
            when(calculateCharge.calculate("BKG-2026000007"))
                    .thenThrow(new AlreadyInvoicedException("すでに発行されています"));

            mockMvc.perform(asAccountant(get("/api/v1/billing/calculations/BKG-2026000007")))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("確定")
    class Confirming {

        @Test
        @DisplayName("確定すると 201 と精算書を返す")
        void issuesTheInvoice() throws Exception {
            when(calculateCharge.confirm(anyString(), any())).thenReturn(issued());

            mockMvc.perform(asAccountant(post("/api/v1/billing/BKG-2026000007/calculate"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"adjustments": [
                                        {"description": "遅延による減額", "amountValue": -10000}
                                    ]}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.invoiceId").value("INV-2026000001"))
                    .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                    .andExpect(jsonPath("$.lineItems[0].description").value("遅延による減額"))
                    .andExpect(jsonPath("$.lineItems[0].amount.value").value(-10000))
                    // **割引率を返す**（22-4）。額だけでは率を復元できない
                    .andExpect(jsonPath("$.discountRate").value(0.1000));
        }

        /** 根拠の無い調整は 400（決定 6）。**利用者の入力の誤りである。** */
        @Test
        @DisplayName("内容の無い調整は 400 を返す")
        void rejectsAdjustmentsWithoutDescription() throws Exception {
            when(calculateCharge.confirm(anyString(), any()))
                    .thenThrow(new IllegalArgumentException("調整の内容を入力してください"));

            mockMvc.perform(asAccountant(post("/api/v1/billing/BKG-2026000007/calculate"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"adjustments": [{"description": " ", "amountValue": -1}]}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("引取が終わっていない予約は確定できない")
        void rejectsCargoThatCannotBeBilled() throws Exception {
            when(calculateCharge.confirm(anyString(), any()))
                    .thenThrow(new BillingNotAvailableException("引取が終わっていない予約です"));

            mockMvc.perform(asAccountant(post("/api/v1/billing/BKG-2026000001/calculate"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"adjustments\": []}"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("予約サービスに届かないとき（IT11 レビュー 中）")
    class BookingServiceDown {

        /**
         * <strong>500 にしない。</strong>
         *
         * <p>経理担当者には「一覧が壊れた」としか見えず、待てば直るのか自分の操作が
         * 悪いのかが分からない。<strong>どちらの分岐で落ちたか</strong>で判定する
         * ——経過時間では判別できない。
         */
        @Test
        @DisplayName("予約サービスに届かないときは 503 と、届いていないことを返す")
        void returnsServiceUnavailableWhenBookingServiceIsDown() throws Exception {
            when(calculateCharge.billable()).thenThrow(
                    new org.springframework.web.client.ResourceAccessException("接続できません"));

            mockMvc.perform(asAccountant(get("/api/v1/billing/unbilled")))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("予約サービスに接続できない")));
        }

        /** 相手が 5xx を返したときも同じ扱いにする。 */
        @Test
        @DisplayName("予約サービスが 5xx を返すときも 503 にする")
        void returnsServiceUnavailableWhenBookingServiceFails() throws Exception {
            when(calculateCharge.calculate("BKG-2026000007")).thenThrow(
                    org.springframework.web.client.HttpServerErrorException.create(
                            org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                            "boom", org.springframework.http.HttpHeaders.EMPTY,
                            new byte[0], null));

            mockMvc.perform(asAccountant(get("/api/v1/billing/calculations/BKG-2026000007")))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    @Nested
    @DisplayName("一覧と詳細")
    class Listing {

        @Test
        @DisplayName("料金未算出の予約を並べる")
        void listsUnbilledBookings() throws Exception {
            when(calculateCharge.billable()).thenReturn(List.of(
                    new BillableCargoSnapshot("BKG-2026000007", "DELIVERED", "1",
                            "丸紅商事株式会社", true, new BigDecimal("0.1000"),
                            new BigDecimal("4200"), "GENERAL", "Tokyo", "Los Angeles", 2,
                            Instant.parse("2027-09-26T00:00:00Z"), null, null)));

            mockMvc.perform(asAccountant(get("/api/v1/billing/unbilled")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].bookingId").value("BKG-2026000007"))
                    .andExpect(jsonPath("$[0].shipperName").value("丸紅商事株式会社"))
                    .andExpect(jsonPath("$[0].shipperType").value("CORPORATE"))
                    .andExpect(jsonPath("$[0].misrouted").value(false))
                    // **金額は載せない。** 一覧を開くだけで全件の計算が走ることになる
                    .andExpect(jsonPath("$[0].totalAmount").doesNotExist());
        }

        /**
         * <strong>最後に荷役があった日時を返す</strong>（IT11 レビュー 中）。
         *
         * <p>引取の日時とは限らない——キャンセルされた予約は引き取っていないが、
         * 途中まで運ばれていれば荷役の記録を持つ。<strong>一覧の並びもこの値で決まる</strong>
         * ので、名前と中身を揃えないと「引取日時で並んでいる」と読まれる。
         */
        @Test
        @DisplayName("キャンセルされた予約でも、最後に荷役があった日時を返す")
        void returnsTheLastHandlingAtForCancelledBookings() throws Exception {
            when(calculateCharge.billable()).thenReturn(List.of(
                    new BillableCargoSnapshot("BKG-2026000010", "CANCELLED", "1",
                            "丸紅商事株式会社", true, new BigDecimal("0.1000"),
                            new BigDecimal("1500"), "GENERAL", "Tokyo", "Los Angeles", 1,
                            Instant.parse("2027-09-08T00:00:00Z"), null,
                            new BillableCargoSnapshot.Cancellation("IN_TRANSIT",
                                    Instant.parse("2027-09-10T00:00:00Z")))));

            mockMvc.perform(asAccountant(get("/api/v1/billing/unbilled")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].cancelled").value(true))
                    .andExpect(jsonPath("$[0].lastHandlingAt").exists());
        }

        @Test
        @DisplayName("発行済みの精算書を並べる")
        void listsInvoices() throws Exception {
            when(invoices.findAll()).thenReturn(List.of(issued()));

            mockMvc.perform(asAccountant(get("/api/v1/billing/invoices")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].invoiceNumber").value("INV-2026000001"))
                    .andExpect(jsonPath("$[0].shipperName").value("丸紅商事株式会社"));
        }

        @Test
        @DisplayName("精算書 1 件を返す")
        void returnsAnInvoice() throws Exception {
            when(invoices.findById("INV-2026000001")).thenReturn(Optional.of(issued()));

            mockMvc.perform(asAccountant(get("/api/v1/billing/invoices/INV-2026000001")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.invoiceNumber").value("INV-2026000001"))
                    .andExpect(jsonPath("$.basis.legCount").value(2))
                    .andExpect(jsonPath("$.taxAmount.value").value(36800))
                    .andExpect(jsonPath("$.totalAmount.value").value(404800));
        }

        @Test
        @DisplayName("見つからない精算書は 404 を返す")
        void returnsNotFoundForAnUnknownInvoice() throws Exception {
            when(invoices.findById("INV-9999999999")).thenReturn(Optional.empty());

            mockMvc.perform(asAccountant(get("/api/v1/billing/invoices/INV-9999999999")))
                    .andExpect(status().isNotFound());
        }
    }
}
