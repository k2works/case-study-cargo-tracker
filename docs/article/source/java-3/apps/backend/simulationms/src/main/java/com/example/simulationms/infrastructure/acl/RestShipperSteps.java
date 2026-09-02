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

    /** 紐付けを読む・書くロール。**実業務でも管理者の操作である**（US33）。 */
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final RestClient gateway;

    /**
     * ロールの利用者として入り直す手立て。
     *
     * <p>荷主の工程は<strong>営業として登録し、管理者として紐付けを読む</strong>
     * ——実業務でも別の人がやる操作である。工程ごとの切符では足りないため、
     * 入り直せる手立てを受け取る。
     */
    private final java.util.function.UnaryOperator<String> loginAs;

    RestShipperSteps(RestClient gateway, java.util.function.UnaryOperator<String> loginAs) {
        this.gateway = gateway;
        this.loginAs = loginAs;
    }

    String execute(ScenarioStep step, String token, Map<String, String> context) {
        return switch (step) {
            case REGISTER_SHIPPER -> registerShipper(token, context);
            case LINK_SHIPPER_USER -> linkShipperUser(token, context);
            default -> throw new BusinessCallFailedException(
                    "荷主の工程ではありません: " + step);
        };
    }

    /**
     * 確認用の利用者にすでに紐付いている荷主があるか。
     *
     * <p><strong>管理者として読む。</strong>内部向けの経路（{@code system:} の名乗り）は
     * 使わない——[ADR-030] 決定 1 が禁じている。専用の読み口を作ると、
     * シミュレーションだけが通る経路ができる。
     *
     * @return 紐付いている荷主 ID。紐付いていなければ空
     */
    private java.util.Optional<Long> linkedDemoShipperId() {
        BusinessMessages.UserShipperLinkResponse response = BusinessCalls.call(
                ScenarioStep.LINK_SHIPPER_USER, () -> gateway.get()
                        .uri(RestBusinessGateway.USER_SHIPPER_LINK_PATH + "/"
                                + RestBusinessGateway.NOTIFICATION_DEMO_USER)
                        .header(HttpHeaders.AUTHORIZATION,
                                BusinessCalls.bearer(loginAs.apply(ADMIN_ROLE)))
                        .retrieve()
                        .body(BusinessMessages.UserShipperLinkResponse.class));
        return response == null ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(response.shipperId());
    }

    String registerShipper(String token, Map<String, String> context) {
        // **確認用の荷主を使い回す。** 実行のたびに新しい荷主を作って紐付け直すと、
        // 継続実行の最中は<strong>一覧を開いた時点と押した時点で荷主が変わる</strong>
        // ——リンクを押すと「自社の貨物として確認できません」になる。
        // 確かめたい場面（継続実行）でこそ使えなくなっていた
        java.util.Optional<Long> reused = linkedDemoShipperId();
        if (reused.isPresent()) {
            return String.valueOf(reused.orElseThrow());
        }
        return registerNewShipper(token, context);
    }

    private String registerNewShipper(String token, Map<String, String> context) {
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
