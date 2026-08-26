package com.example.billingms.infrastructure.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.contract.BillingSnapshotContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 精算の完了を予約に知らせる ACL（US23-4・[ADR-028] 決定 1）。
 *
 * <p><strong>本 IT で増えた結合方向である。</strong>これまで billingms → bookingms は
 * 読み取りだけだった。ここで初めて相手の状態を動かす。
 */
@DisplayName("精算完了の通知（ACL）")
class RestBookingSettlementNotifierTest {

    private static final String BASE = "http://booking.test";

    private MockRestServiceServer server;

    private RestBookingSettlementNotifier notifier;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        notifier = new RestBookingSettlementNotifier(builder.build());
    }

    @Test
    @DisplayName("予約番号を経路に載せ、システムとして名乗る")
    void postsToTheAgreedPathAsTheSystem() {
        server.expect(requestTo(BASE + "/api/v1/bookings/BKG-2026000007/settlement"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(AuthenticatedUser.USER_ID_HEADER, "system:billingms"))
                .andRespond(withNoContent());

        notifier.markSettled("BKG-2026000007");

        server.verify();
    }

    /**
     * <strong>断られたら黙って進まない</strong>（[ADR-028] 決定 1）。
     *
     * <p>捨てると、引取済のまま残った予約に誰も気づけない——例外にしないことは、
     * 記録しないことではない。入金の確認ごと巻き戻す方が、経理担当者にとって
     * 分かりやすい（もう一度押せる）。
     */
    @Test
    @DisplayName("相手が断ったら、その失敗を外へ出す")
    void doesNotSwallowRejections() {
        server.expect(requestTo(BASE + "/api/v1/bookings/BKG-2026000007/settlement"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> notifier.markSettled("BKG-2026000007"))
                .as("相手の拒否を握りつぶしている。予約が引取済のまま残る")
                .isInstanceOf(Exception.class);
    }

    /** 呼び出す経路と主体は契約が決める。**写して持つと、片方だけ直る。** */
    @Test
    @DisplayName("呼び出す経路と主体は契約から取る")
    void usesThePathAndPrincipalFromTheContract() {
        assertThat(RestBookingSettlementNotifier.SETTLEMENT_PATH)
                .as("知らせる経路が契約と食い違っている。相手は 404 を返す")
                .isEqualTo(BillingSnapshotContract.SETTLEMENT_PATH);
        assertThat(RestBookingSettlementNotifier.SYSTEM_PRINCIPAL)
                .isEqualTo(BillingSnapshotContract.CALLER_PRINCIPAL);
    }
}
