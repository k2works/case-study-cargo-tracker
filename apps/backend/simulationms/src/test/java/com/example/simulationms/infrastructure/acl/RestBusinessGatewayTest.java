package com.example.simulationms.infrastructure.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 業務 API を Gateway 経由で踏む出口（[ADR-030] 決定 2）。
 *
 * <p><strong>実在の利用者としてログインしてから呼ぶ</strong>ことを、ここで固定する。
 * 名乗りだけで通る経路（{@code system:} や内部 API）に替えると、実利用者の操作が 403 で
 * 止まっていてもシミュレーションだけが通る——この仕組みを作った理由が失われる。
 */
@DisplayName("業務 API の呼び出し")
class RestBusinessGatewayTest {

    private static final String BASE = "http://gateway.test";

    private MockRestServiceServer server;

    private RestBusinessGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new RestBusinessGateway(builder.build(), SimulationUsers.of(
                Map.of("ROLE_SALES", "sales01", "ROLE_ROUTING", "routing01",
                        "ROLE_HANDLER", "handler01", "ROLE_TRACKER", "tracker01",
                        "ROLE_ACCOUNTANT", "accountant01"), "password"),
                Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"), ZoneId.of("Asia/Tokyo")));
    }

    private void expectLoginAs(String username, String token) {
        server.expect(requestTo(BASE + RestBusinessGateway.LOGIN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.userId").value(username))
                .andExpect(jsonPath("$.password").value("password"))
                .andRespond(withSuccess(
                        "{\"token\":\"" + token + "\",\"userId\":\"" + username + "\"}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("その工程を踏むロールの利用者としてログインし、受け取った切符で業務 API を呼ぶ")
    void logsInAsTheRoleOfTheStep() {
        expectLoginAs("sales01", "token-sales");
        server.expect(requestTo(BASE + RestBusinessGateway.SHIPPER_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-sales"))
                .andExpect(jsonPath("$.type").value("CORPORATE"))
                // **由来が分かる帯で採番させる**（[ADR-030] 決定 3）。
                // 送り忘れると、実データに混ざったまま経理の締めに乗る
                .andExpect(jsonPath("$.simulated").value(true))
                .andRespond(withSuccess("{\"id\":42,\"shipperCode\":\"SH-0042\"}",
                        MediaType.APPLICATION_JSON));

        String identifier = gateway.execute(ScenarioStep.REGISTER_SHIPPER,
                Map.of(BusinessContextKey.RUN_ID, "SIM-20261116-0001"));

        assertThat(identifier).isEqualTo("42");
        server.verify();
    }

    /**
     * <strong>工程ごとにログインし直す</strong>（[ADR-030] 決定 2）。
     *
     * <p>1 つの利用者に全ロールを与えると、本番には存在しない権限の持ち主ができる。
     */
    @Test
    @DisplayName("ロールの違う工程は、違う利用者としてログインする")
    void logsInAgainForEachRole() {
        assertThat(ScenarioStep.REGISTER_SHIPPER.role())
                .isNotEqualTo(ScenarioStep.ASSIGN_ROUTE.role());
    }

    @Test
    @DisplayName("ログインに失敗したら、誰として入ろうとしたかを添えて止まる")
    void namesTheUserWhenLoginFails() {
        server.expect(requestTo(BASE + RestBusinessGateway.LOGIN_PATH))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.REGISTER_SHIPPER, Map.of()))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("sales01");
    }

    @Test
    @DisplayName("業務 API が断ったら、その工程と応答の状態を添えて止まる")
    void namesTheStepWhenTheBusinessCallFails() {
        expectLoginAs("sales01", "token-sales");
        server.expect(requestTo(BASE + RestBusinessGateway.SHIPPER_PATH))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.REGISTER_SHIPPER, Map.of()))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining(ScenarioStep.REGISTER_SHIPPER.label())
                .hasMessageContaining("403");
    }

    @Test
    @DisplayName("受け取り・積込・荷降しを 3 つとも記録する")
    void recordsAllThreeHandlingActivities() {
        expectLoginAs("handler01", "token-handler");
        server.expect(org.springframework.test.web.client.ExpectedCount.times(3),
                        requestTo(BASE + RestBusinessGateway.HANDLING_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.trackingNumber").value("TRK-1"))
                .andRespond(withStatus(HttpStatus.CREATED));

        gateway.execute(ScenarioStep.RECORD_HANDLING, Map.of(
                BusinessContextKey.TRACKING_NUMBER, "TRK-1",
                BusinessContextKey.VOYAGE_NUMBER, "V-SIM-1"));

        // **3 件そろって初めて引取が成り立つ。**途中を飛ばすと、引取が断られる形でしか
        // 気づけない——原因は飛ばしたこちらにある
        server.verify();
    }

    @Test
    @DisplayName("通関を申告し、申告の識別子を引き継ぐ")
    void declaresCustoms() {
        expectLoginAs("handler01", "token-handler");
        server.expect(requestTo(BASE + RestBusinessGateway.CUSTOMS_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.trackingNumber").value("TRK-1"))
                .andRespond(withSuccess("{\"declarationId\":7}", MediaType.APPLICATION_JSON));

        String identifier = gateway.execute(ScenarioStep.DECLARE_CUSTOMS,
                Map.of(BusinessContextKey.TRACKING_NUMBER, "TRK-1"));

        assertThat(identifier).isEqualTo("7");
        server.verify();
    }

    /** 通関済にするのは追跡管理者である。荷役作業員が自分で通せると、審査が形だけになる。 */
    @Test
    @DisplayName("通関済にするのは追跡管理者である")
    void clearsCustomsAsTheTracker() {
        expectLoginAs("tracker01", "token-tracker");
        server.expect(requestTo(BASE + RestBusinessGateway.CUSTOMS_PATH + "/7/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.status").value("CLEARED"))
                .andExpect(jsonPath("$.reason").isNotEmpty())
                .andRespond(withSuccess());

        gateway.execute(ScenarioStep.CLEAR_CUSTOMS,
                Map.of(BusinessContextKey.DECLARATION_ID, "7"));

        server.verify();
    }

    @Test
    @DisplayName("引取には荷受人の確認を添える")
    void recordsTheClaimWithTheConsigneeConfirmation() {
        expectLoginAs("handler01", "token-handler");
        server.expect(requestTo(BASE + RestBusinessGateway.HANDLING_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.type").value("CLAIM"))
                .andExpect(jsonPath("$.consigneeConfirmation").isNotEmpty())
                .andRespond(withStatus(HttpStatus.CREATED));

        gateway.execute(ScenarioStep.RECORD_CLAIM,
                Map.of(BusinessContextKey.TRACKING_NUMBER, "TRK-1"));

        server.verify();
    }

    @Test
    @DisplayName("料金を算出し、精算書の番号を引き継ぐ")
    void calculatesTheCharge() {
        expectLoginAs("accountant01", "token-accountant");
        server.expect(requestTo(BASE + RestBusinessGateway.BILLING_PATH + "/BK-0001/calculate"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"invoiceNumber\":\"INV-0001\"}",
                        MediaType.APPLICATION_JSON));

        String identifier = gateway.execute(ScenarioStep.CALCULATE_CHARGE,
                Map.of(BusinessContextKey.BOOKING_ID, "BK-0001"));

        assertThat(identifier).isEqualTo("INV-0001");
        server.verify();
    }

    /**
     * <strong>金額は精算書から読む。</strong>
     *
     * <p>こちらで計算し直すと、料金の式が 2 つに増える——実装が正しいかを確かめる仕組みが、
     * 自分の式で答え合わせをすることになる。
     */
    @Test
    @DisplayName("精算では、精算書に書かれた金額をそのまま入金する")
    void paysExactlyWhatTheInvoiceSays() {
        expectLoginAs("accountant01", "token-accountant");
        server.expect(requestTo(BASE + RestBusinessGateway.BILLING_PATH + "/invoices/INV-0001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"invoiceNumber":"INV-0001",
                         "totalAmount":{"value":1260000,"currency":"JPY"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                        BASE + RestBusinessGateway.BILLING_PATH + "/invoices/INV-0001/payment"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amountValue").value(1260000))
                .andExpect(jsonPath("$.method").value("BANK_TRANSFER"))
                .andRespond(withSuccess());

        gateway.execute(ScenarioStep.SETTLE, Map.of(BusinessContextKey.INVOICE_NUMBER, "INV-0001"));

        server.verify();
    }

    /**
     * 依頼・通知・確定は<strong>同じ形で呼ぶ</strong>。
     *
     * <p>工程ごとに書き分けると、書き分けた 1 つだけが違う経路を向いても気づけない。
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "REQUEST_ROUTING,sales01,POST,/routing-request",
            "NOTIFY_ROUTE,sales01,POST,/route-notification",
            "CONFIRM_BOOKING,sales01,PUT,/confirm"})
    @DisplayName("依頼・通知・確定は、予約の経路をそのロールで踏む")
    void callsTheBookingActions(String step, String username, String httpMethod, String suffix) {
        expectLoginAs(username, "token");
        server.expect(requestTo(BASE + RestBusinessGateway.BOOKING_PATH + "/BK-0001" + suffix))
                .andExpect(method(HttpMethod.valueOf(httpMethod)))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess());

        String identifier = gateway.execute(ScenarioStep.valueOf(step),
                Map.of(BusinessContextKey.BOOKING_ID, "BK-0001"));

        assertThat(identifier).isEmpty();
        server.verify();
    }

    /**
     * <strong>空の応答を成功にしない。</strong>
     *
     * <p>200 が返っても中身が無ければ、次の工程は引き継ぐものを持たない。
     * 空文字で先へ進めると、原因の無い 404 が後ろの工程で出る。
     */
    @Test
    @DisplayName("応答に識別子が無ければ、成功にせず止まる")
    void stopsWhenTheResponseCarriesNoIdentifier() {
        expectLoginAs("sales01", "token-sales");
        server.expect(requestTo(BASE + RestBusinessGateway.SHIPPER_PATH))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.REGISTER_SHIPPER, Map.of()))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("荷主");
    }

    @Test
    @DisplayName("精算書に金額が無ければ、入金を送らずに止まる")
    void stopsWhenTheInvoiceHasNoAmount() {
        expectLoginAs("accountant01", "token-accountant");
        server.expect(requestTo(BASE + RestBusinessGateway.BILLING_PATH + "/invoices/INV-0001"))
                .andRespond(withSuccess("{\"invoiceNumber\":\"INV-0001\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.SETTLE,
                Map.of(BusinessContextKey.INVOICE_NUMBER, "INV-0001")))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("INV-0001");

        // 金額が読めないまま入金を送っていない（送っていれば期待していない要求として落ちる）
        server.verify();
    }

    @Test
    @DisplayName("ログインの応答に切符が無ければ、業務 API を呼ばずに止まる")
    void stopsWhenTheLoginCarriesNoToken() {
        server.expect(requestTo(BASE + RestBusinessGateway.LOGIN_PATH))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.REGISTER_SHIPPER, Map.of()))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("切符");
        server.verify();
    }

    @Test
    @DisplayName("料金算出の応答に精算書が無ければ、成功にせず止まる")
    void stopsWhenTheCalculationCarriesNoInvoice() {
        expectLoginAs("accountant01", "token-accountant");
        server.expect(requestTo(BASE + RestBusinessGateway.BILLING_PATH + "/BK-0001/calculate"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.CALCULATE_CHARGE,
                Map.of(BusinessContextKey.BOOKING_ID, "BK-0001")))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("精算書");
    }

    @Test
    @DisplayName("通関申告の応答に申告が無ければ、成功にせず止まる")
    void stopsWhenTheDeclarationCarriesNoId() {
        expectLoginAs("handler01", "token-handler");
        server.expect(requestTo(BASE + RestBusinessGateway.CUSTOMS_PATH))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.DECLARE_CUSTOMS,
                Map.of(BusinessContextKey.TRACKING_NUMBER, "TRK-1")))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("申告");
    }

    @Test
    @DisplayName("追跡番号発行の応答に番号が無ければ、成功にせず止まる")
    void stopsWhenNoTrackingNumberComesBack() {
        expectLoginAs("routing01", "token-routing");
        server.expect(requestTo(
                        BASE + RestBusinessGateway.BOOKING_PATH + "/BK-0001/tracking-number"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.ISSUE_TRACKING_NUMBER,
                Map.of(BusinessContextKey.BOOKING_ID, "BK-0001")))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("追跡番号");
    }

    @Test
    @DisplayName("予約登録の応答に予約番号が無ければ、成功にせず止まる")
    void stopsWhenNoBookingIdComesBack() {
        expectLoginAs("sales01", "token-sales");
        server.expect(requestTo(BASE + RestBusinessGateway.BOOKING_PATH))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.REGISTER_BOOKING,
                Map.of(BusinessContextKey.SHIPPER_ID, "42")))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("予約番号");
    }
}
