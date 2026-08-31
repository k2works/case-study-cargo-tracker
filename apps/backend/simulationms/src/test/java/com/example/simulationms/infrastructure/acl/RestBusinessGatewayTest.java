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
import com.example.simulationms.application.internal.outboundservices.acl.BusinessContext;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
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
                Map.of("ROLE_SALES", "sales01", "ROLE_ROUTING", "routing01"), "password"));
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
                Map.of(BusinessContext.RUN_ID, "SIM-20261116-0001"));

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
    @DisplayName("まだ実装していない工程は、成功にせず止まる")
    void doesNotPretendUnimplementedStepsSucceeded() {
        expectLoginAs("routing01", "token-routing");

        assertThatThrownBy(() -> gateway.execute(ScenarioStep.ASSIGN_ROUTE, Map.of()))
                .isInstanceOf(BusinessCallFailedException.class)
                .hasMessageContaining(ScenarioStep.ASSIGN_ROUTE.label());
    }
}
