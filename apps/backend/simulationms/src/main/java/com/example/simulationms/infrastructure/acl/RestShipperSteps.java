package com.example.simulationms.infrastructure.acl;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * 荷主を用意する工程（[ADR-030] 決定 2 の 1 ポートの内側）。
 *
 * <p>登録（US34）と、確認用の利用者への紐付け（US39）。<strong>出口が増えたのではない</strong>
 * ——{@link RestBusinessGateway} が業務 API を呼ぶ唯一の出口である点は変わらない。
 * 分けたのは、ここが変わる理由（荷主の登録内容・紐付けの決まり）が他の工程と違うためである。
 */
class RestShipperSteps {

    /** 業務データに残す名乗り。 */
    private static final String OPERATOR = RestBusinessGateway.OPERATOR;

    private final RestClient gateway;

    RestShipperSteps(RestClient gateway) {
        this.gateway = gateway;
    }

    String execute(ScenarioStep step, String token, Map<String, String> context) {
        return switch (step) {
            case REGISTER_SHIPPER -> registerShipper(token, context);
            case LINK_SHIPPER_USER -> linkShipperUser(token, context);
            default -> throw new BusinessCallFailedException(
                    "荷主の工程ではありません: " + step);
        };
    }

    String registerShipper(String token, Map<String, String> context) {
        String marker = BusinessCalls.runId(context);
        BusinessMessages.ShipperResponse response = BusinessCalls.call(ScenarioStep.REGISTER_SHIPPER, () -> gateway.post()
                .uri(RestBusinessGateway.SHIPPER_PATH)
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .body(new BusinessMessages.ShipperRequest(
                        // **個人にする。**法人は契約番号が要り、無いと集約が断る——
                        // 確かめたいのは業務の道のりであって、契約の妥当性ではない
                        "INDIVIDUAL",
                        OPERATOR + "荷主 " + marker,
                        marker.toLowerCase(Locale.ROOT) + "@simulation.example.com",
                        "東京都千代田区 1-1-1",
                        "03-0000-0000",
                        true,
                        // **シミュレーション由来として登録する**（[ADR-030] 決定 3）。
                        // 送り忘れると、実データに混ざったまま経理の締めに乗る
                        true))
                .retrieve()
                .body(BusinessMessages.ShipperResponse.class));

        if (response == null || response.id() == null) {
            throw new BusinessCallFailedException("荷主登録の応答に荷主がありません");
        }
        return String.valueOf(response.id());
    }

    /**
     * 作った荷主を、確認用の利用者（{@value RestBusinessGateway#NOTIFICATION_DEMO_USER}）に紐付ける（US39）。
     *
     * <p><strong>実業務と同じ API を通る。</strong>管理者が利用者と荷主を結ぶのは US33 の
     * 操作であり、シミュレーション専用の経路は作らない（[ADR-030] 決定 1）。
     *
     * <p><strong>紐付けは上書きである。</strong>実行のたびに新しい荷主ができるため、
     * 確認用の利用者は<strong>いつでも最後の実行の荷主</strong>を見る。過去の実行の貨物は
     * 見えなくなる——確かめたいのは「いま流したものが届くか」である。
     */
    String linkShipperUser(String token, Map<String, String> context) {
        String shipperId = BusinessCalls.required(context, BusinessContextKey.SHIPPER_ID);
        BusinessCalls.call(ScenarioStep.LINK_SHIPPER_USER, () -> gateway.put()
                .uri(RestBusinessGateway.USER_SHIPPER_LINK_PATH + "/" + RestBusinessGateway.NOTIFICATION_DEMO_USER)
                .header(HttpHeaders.AUTHORIZATION, BusinessCalls.bearer(token))
                .body(new BusinessMessages.UserShipperLinkRequest(Long.valueOf(shipperId)))
                .retrieve()
                .body(String.class));
        return RestBusinessGateway.NOTIFICATION_DEMO_USER;
    }
}
