package com.example.bookingms.infrastructure.billing;

import com.example.bookingms.application.port.ChargeQuoteFinder;
import com.example.shared.auth.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.client.RestClient;

/**
 * 料金の試算を billingms に問う ACL（US01-3・[ADR-028] 決定 6）。
 *
 * <p><strong>相手の型は持ち込まない。</strong>応答は専用の record（{@link QuoteResponse}）で
 * 受け、こちらの言葉（{@code BigDecimal}）へ移す。
 */
// **経路は設定にしない。**相手との契約であり、環境ごとに変わるものではない
// （変わるのは所在＝ベース URL だけで、そちらは設定から受け取る）。契約テストが
// 両側の経路を突き合わせており、片方だけ直せば赤になる
@SuppressWarnings("java:S1075")
public class RestChargeQuoteFinder implements ChargeQuoteFinder {

    /** 呼び出す経路。**契約と突き合わせる**（契約テスト）。 */
    public static final String QUOTE_PATH = "/api/v1/billing/quotes";

    /** このサービス自身を表す主体。名乗らないと相手の [ADR-007] フィルタが一律に断る。 */
    public static final String SYSTEM_PRINCIPAL = "system:bookingms";

    private final RestClient restClient;

    public RestChargeQuoteFinder(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public BigDecimal quote(List<QuoteLeg> legs, BigDecimal weightKg, String cargoType) {
        QuoteResponse response = restClient.post()
                .uri(QUOTE_PATH)
                .header(AuthenticatedUser.USER_ID_HEADER, SYSTEM_PRINCIPAL)
                .body(new QuoteRequest(
                        legs.stream()
                                .map(leg -> new QuoteRequest.QuoteLegRequest(
                                        leg.loadRegion(), leg.unloadRegion()))
                                .toList(),
                        weightKg, cargoType))
                .retrieve()
                .body(QuoteResponse.class);

        if (response == null || response.baseAmount() == null) {
            throw new IllegalStateException("料金試算の応答が空です");
        }
        return response.baseAmount().value();
    }

    /** 依頼。**係数は送らない**——式は相手が持つ。 */
    record QuoteRequest(List<QuoteLegRequest> legs, BigDecimal weightKg, String cargoType) {

        record QuoteLegRequest(String loadRegion, String unloadRegion) {
        }
    }

    /** 応答。**知らない項目は無視する**（相手が項目を足しただけでこちらが落ちない）。 */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record QuoteResponse(MoneyResponse baseAmount) {

        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        record MoneyResponse(BigDecimal value, String currency) {
        }
    }
}
