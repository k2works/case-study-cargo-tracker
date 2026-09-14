package com.example.cargotracker.simulation.infrastructure.api;

import com.example.cargotracker.simulation.domain.model.valueobjects.StepRole;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/**
 * Gateway を人と同じように叩く（[ADR-0020] 決定 2）。
 *
 * <p><b>ロールごとのトークンを添える。</b> 全部を管理者で叩くと認可を踏まない
 * ——「シミュレーションは通るのに実際の操作は通らない」状態を検出できなくなる。</p>
 *
 * <p><b>断られたことを例外にしない。</b> 4xx・5xx は「工程が止まった」という
 * 業務上の結果なので、状態と本文を持ったまま呼び出し元へ返す——例外にすると
 * 「どの工程がどう断られたか」が失われる（US34 §受入基準 2）。</p>
 */
public class GatewayCalls {

    private final RestClient client;
    private final GatewayTokens tokens;

    public GatewayCalls(RestClient client, GatewayTokens tokens) {
        this.client = client;
        this.tokens = tokens;
    }

    /** 応答。<b>成功でなければ本文をそのまま持つ</b>。 */
    public record Response(int status, String body) {

        /** 通ったか。 */
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** 送る。 */
    public Response post(StepRole role, String uri, Object body) {
        return exchange(role, "POST", uri, body);
    }

    /** 読む。 */
    public Response get(StepRole role, String uri) {
        return exchange(role, "GET", uri, null);
    }

    private Response exchange(StepRole role, String method, String uri, Object body) {
        RestClient.RequestBodySpec spec = client
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(uri)
                .header("Authorization", "Bearer " + tokens.of(role));
        if (body != null) {
            spec = spec.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body);
        }
        // **状態で分岐させない。** onStatus を入れないと 4xx で例外になり、
        // 断られた理由が呼び出し元に届かない。
        return spec.exchange((request, response) -> {
            HttpStatusCode status = response.getStatusCode();
            String text = new String(response.getBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            return new Response(status.value(), text);
        }, false);
    }
}
