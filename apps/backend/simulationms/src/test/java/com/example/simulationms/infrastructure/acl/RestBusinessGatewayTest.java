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
                        "ROLE_HANDLER", "handler01"), "password"),
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

    /**
     * まだ実装していない工程を<strong>成功として返さない</strong>。
     *
     * <p>空文字を返して先へ進めると、何も呼んでいない実行が「全工程成功」で終わる——
     * 確かめているつもりで何も確かめていない状態になる。
     */
    @Test
    @DisplayName("予約を登録し、前の工程が引き継いだ荷主に紐づける")
    void registersTheBookingForTheShipperFromTheContext() {
        expectLoginAs("sales01", "token-sales");
        server.expect(requestTo(BASE + RestBusinessGateway.BOOKING_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-sales"))
                .andExpect(jsonPath("$.shipperId").value(42))
                .andExpect(jsonPath("$.originUnLocode").value("JPTYO"))
                .andExpect(jsonPath("$.destinationUnLocode").value("USLAX"))
                // 業務タイムゾーンの暦で数える。UTC で「今日」を決めると時差の分だけずれる
                .andExpect(jsonPath("$.arrivalDeadline").value("2027-03-16"))
                .andRespond(withSuccess("{\"bookingId\":\"BK-0001\"}",
                        MediaType.APPLICATION_JSON));

        String identifier = gateway.execute(ScenarioStep.REGISTER_BOOKING,
                Map.of(BusinessContextKey.SHIPPER_ID, "42"));

        assertThat(identifier).isEqualTo("BK-0001");
        server.verify();
    }

    /**
     * <strong>引き継ぎが切れていることを、業務 API の失敗に化けさせない。</strong>
     *
     * <p>空のまま呼ぶと存在しない予約への操作になり、404 として現れる——
     * 原因は前の工程にあるのに、後ろの工程が悪いように見える。
     */
    @Test
    @DisplayName("前の工程が識別子を引き継いでいなければ、その名前を挙げて止まる")
    void namesTheMissingIdentifier() {
        expectLoginAs("sales01", "token-sales");

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.REQUEST_ROUTING, Map.of()))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining(BusinessContextKey.BOOKING_ID);
    }

    @Test
    @DisplayName("航海を登録し、シミュレーションと分かる番号を引き継ぐ")
    void registersAVoyageNamedAfterTheRun() {
        expectLoginAs("routing01", "token-routing");
        server.expect(requestTo(BASE + RestBusinessGateway.VOYAGE_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.voyageNumber").value("V-SIM-20261116-0001"))
                .andExpect(jsonPath("$.movements[0].departureUnLocode").value("JPTYO"))
                .andRespond(withStatus(HttpStatus.CREATED));

        String identifier = gateway.execute(ScenarioStep.REGISTER_VOYAGE,
                Map.of(BusinessContextKey.RUN_ID, "SIM-20261116-0001"));

        assertThat(identifier).isEqualTo("V-SIM-20261116-0001");
        server.verify();
    }

    @Test
    @DisplayName("候補を引いて先頭を割り当てる")
    void assignsTheFirstCandidate() {
        expectLoginAs("routing01", "token-routing");
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE + RestBusinessGateway.ROUTE_PATH + "?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"candidates":[{"legs":[{"voyageNumber":"V-1",
                          "fromUnLocode":"JPTYO","toUnLocode":"USLAX",
                          "departureTime":"2026-11-17T09:00:00Z",
                          "arrivalTime":"2026-12-06T09:00:00Z"}]}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + RestBusinessGateway.BOOKING_PATH + "/BK-0001/route"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.legs[0].voyageNumber").value("V-1"))
                .andExpect(jsonPath("$.legs[0].loadUnLocode").value("JPTYO"))
                .andExpect(jsonPath("$.legs[0].loadTime").value("2026-11-17T09:00:00Z"))
                .andRespond(withSuccess());

        gateway.execute(ScenarioStep.ASSIGN_ROUTE,
                Map.of(BusinessContextKey.BOOKING_ID, "BK-0001"));

        server.verify();
    }

    /**
     * 候補 0 件は<strong>飛ばさない</strong>（[ADR-030] 拡張 3a）。
     *
     * <p>飛ばすと、何も運ばないまま全工程が成功で終わる。
     */
    @Test
    @DisplayName("経路候補が 0 件なら、条件を添えて止まる")
    void stopsWhenThereIsNoCandidate() {
        expectLoginAs("routing01", "token-routing");
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE + RestBusinessGateway.ROUTE_PATH + "?")))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.ASSIGN_ROUTE,
                Map.of(BusinessContextKey.BOOKING_ID, "BK-0001")))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining("0 件")
                .hasMessageContaining("JPTYO");
    }

    @Test
    @DisplayName("追跡番号を発行して引き継ぐ")
    void issuesTheTrackingNumber() {
        expectLoginAs("routing01", "token-routing");
        server.expect(requestTo(
                        BASE + RestBusinessGateway.BOOKING_PATH + "/BK-0001/tracking-number"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"trackingNumber\":\"TRK-20261116-0001\"}",
                        MediaType.APPLICATION_JSON));

        String identifier = gateway.execute(ScenarioStep.ISSUE_TRACKING_NUMBER,
                Map.of(BusinessContextKey.BOOKING_ID, "BK-0001"));

        assertThat(identifier).isEqualTo("TRK-20261116-0001");
        server.verify();
    }

    @Test
    @DisplayName("まだ実装していない工程は、成功にせず止まる")
    void doesNotPretendUnimplementedStepsSucceeded() {
        expectLoginAs("handler01", "token-handler");

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.RECORD_HANDLING, Map.of()))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining(ScenarioStep.RECORD_HANDLING.label());
    }
}
