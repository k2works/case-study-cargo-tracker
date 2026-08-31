package com.example.simulationms.infrastructure.acl;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
import com.example.simulationms.application.internal.outboundservices.acl.BusinessContext;
import com.example.simulationms.application.internal.outboundservices.acl.BusinessGateway;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

/**
 * 業務 API を Gateway 経由で踏む出口（[ADR-030] 決定 2）。
 *
 * <p><strong>工程ごとにログインし直す。</strong>切符を使い回すと、1 つの利用者に
 * 全ロールを与えたのと同じ状態になる——本番には存在しない権限の持ち主が生まれる。
 * ログインの往復は増えるが、確かめたいのは「そのロールでその操作が通るか」である。
 *
 * <p><strong>経路は設定にしない。</strong>相手との契約であり、環境ごとに変わるのは
 * 所在（ベース URL）だけである。
 */
@SuppressWarnings("java:S1075")
public class RestBusinessGateway implements BusinessGateway {

    /** ログインの経路。Gateway が認証不要で通す唯一の POST である。 */
    public static final String LOGIN_PATH = "/api/v1/auth/login";

    /** 荷主登録の経路。 */
    public static final String SHIPPER_PATH = "/api/v1/shippers";

    private final RestClient gateway;
    private final SimulationUsers users;

    public RestBusinessGateway(RestClient gateway, SimulationUsers users) {
        this.gateway = gateway;
        this.users = users;
    }

    @Override
    public String execute(ScenarioStep step, Map<String, String> context) {
        String token = login(step.role());

        // **既定の分岐を置かない。**置くと、工程を足したときに何も呼ばないまま
        // 「成功」で通り抜ける。列挙を網羅させ、足した工程が必ずここへ現れるようにする
        return switch (step) {
            case REGISTER_SHIPPER -> registerShipper(token, context);
            case REGISTER_BOOKING, REQUEST_ROUTING, ASSIGN_ROUTE, CONFIRM_BOOKING,
                    ISSUE_TRACKING_NUMBER, RECORD_HANDLING, DECLARE_CUSTOMS, CLEAR_CUSTOMS,
                    RECORD_CLAIM, CALCULATE_CHARGE, SETTLE ->
                throw new BusinessCallFailedException(
                        step.label() + " はまだ実装していません（IT14 Phase 3.2〜3.3）");
        };
    }

    /**
     * その工程を踏むロールの利用者として入る。
     *
     * <p>失敗したら<strong>誰として入ろうとしたか</strong>を添える。
     * 「ログインに失敗しました」だけでは、名簿の設定が違うのか利用者が消えているのかを
     * 切り分けられない。
     */
    private String login(String role) {
        String username = users.usernameFor(role);
        try {
            LoginResponse response = gateway.post()
                    .uri(LOGIN_PATH)
                    .body(new LoginRequest(username, users.password()))
                    .retrieve()
                    .body(LoginResponse.class);

            if (response == null || response.token() == null || response.token().isBlank()) {
                throw new BusinessCallFailedException(
                        "ログインの応答に切符がありません: " + username);
            }
            return response.token();
        } catch (RestClientException e) {
            throw new BusinessCallFailedException(
                    "ログインできません: " + username + "（" + describe(e) + "）", e);
        }
    }

    private String registerShipper(String token, Map<String, String> context) {
        String marker = context.getOrDefault(BusinessContext.RUN_ID, "SIM");
        ShipperResponse response = call(ScenarioStep.REGISTER_SHIPPER, () -> gateway.post()
                .uri(SHIPPER_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new ShipperRequest(
                        "CORPORATE",
                        "シミュレーション荷主 " + marker,
                        marker.toLowerCase(java.util.Locale.ROOT) + "@simulation.example.com",
                        "東京都千代田区 1-1-1",
                        "03-0000-0000",
                        true))
                .retrieve()
                .body(ShipperResponse.class));

        if (response == null || response.id() == null) {
            throw new BusinessCallFailedException("荷主登録の応答に荷主がありません");
        }
        return String.valueOf(response.id());
    }

    /** 業務 API の失敗に<strong>工程の名前と応答の状態</strong>を添える。 */
    private <T> T call(ScenarioStep step, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            throw new BusinessCallFailedException(
                    step.label() + " が失敗しました（" + describe(e) + "）", e);
        }
    }

    private static String describe(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            return String.valueOf(response.getStatusCode().value());
        }
        return e.getMessage();
    }

    record LoginRequest(String userId, String password) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LoginResponse(String token) {
    }

    /** 荷主登録の依頼。相手の型は持ち込まない。 */
    record ShipperRequest(String type, String name, String email, String address, String phone,
            boolean registerAnyway) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ShipperResponse(Long id, String shipperCode) {
    }
}
