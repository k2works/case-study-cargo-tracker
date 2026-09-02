package com.example.bookingms.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.bookingms.application.internal.outboundservices.acl.ChargeQuoteFinder;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.contract.ChargeQuoteContract;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 料金試算の ACL（US01-3・[ADR-028] 決定 6）。**コンシューマ側の契約テスト**。
 *
 * <p>ここで固定した期待（経路・名乗り・依頼の形・応答の読み方）を、プロバイダ側
 * （billingms の {@code QuoteControllerTest}）が同じ形で満たす。
 */
@DisplayName("料金試算の取得（ACL）")
class RestChargeQuoteFinderTest {

    private static final String BASE = "http://billing.test";

    private MockRestServiceServer server;

    private RestChargeQuoteFinder finder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        finder = new RestChargeQuoteFinder(builder.build());
    }

    @Test
    @DisplayName("区間の地域区分と重量・貨物種別を送り、基本料金を受け取る")
    void sendsTheLegsAndReadsTheBaseAmount() {
        server.expect(requestTo(BASE + ChargeQuoteContract.PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(AuthenticatedUser.USER_ID_HEADER, "system:bookingms"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.legs[0].loadRegion").value("DOMESTIC"))
                .andExpect(jsonPath("$.legs[0].unloadRegion").value("OCEAN"))
                .andExpect(jsonPath("$.weightKg").value(4200))
                .andExpect(jsonPath("$.cargoType").value("GENERAL"))
                .andRespond(withSuccess("""
                        {"baseAmount": {"value": 1260000, "currency": "JPY"}}
                        """, MediaType.APPLICATION_JSON));

        BigDecimal quoted = finder.quote(
                List.of(new ChargeQuoteFinder.QuoteLeg("DOMESTIC", "OCEAN")),
                new BigDecimal("4200"), "GENERAL");

        assertThat(quoted).isEqualByComparingTo("1260000");
        server.verify();
    }

    /**
     * <strong>係数は送らない</strong>（[ADR-028] 決定 6）。
     *
     * <p>送れるようにすると、そこが 2 つ目の式になる——見積と請求がずれる道が開く。
     */
    @Test
    @DisplayName("依頼に載るのは、契約が決めた項目だけである")
    void sendsOnlyTheAgreedFields() {
        assertThat(ChargeQuoteContract.REQUEST_FIELDS)
                .as("依頼の項目が契約と食い違っている")
                .containsExactly("legs", "weightKg", "cargoType")
                .doesNotContain("legFactor", "weightFactor", "cargoTypeFactor", "baseFare");
    }

    /** 呼び出す経路と主体は契約が決める。**写して持つと、片方だけ直る。** */
    @Test
    @DisplayName("呼び出す経路と主体は契約から取る")
    void usesThePathAndPrincipalFromTheContract() {
        assertThat(RestChargeQuoteFinder.QUOTE_PATH).isEqualTo(ChargeQuoteContract.PATH);
        assertThat(RestChargeQuoteFinder.SYSTEM_PRINCIPAL)
                .isEqualTo(ChargeQuoteContract.CALLER_PRINCIPAL);
    }
}
