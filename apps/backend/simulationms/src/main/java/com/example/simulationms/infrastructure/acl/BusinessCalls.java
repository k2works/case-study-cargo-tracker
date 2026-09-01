package com.example.simulationms.infrastructure.acl;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 業務 API を呼ぶときの共通の道具。
 *
 * <p>正常系（{@link RestBusinessGateway}）と例外系（{@link RestExceptionSteps}）で
 * 同じ失敗の述べ方・同じ引き継ぎの読み方を使う。<strong>片方だけが状態しか述べない</strong>
 * 形になると、切り分けの手がかりが工程によって変わる。
 *
 * <p>出口そのものは増やさない。[ADR-030] 決定 2 の「出口は 1 ポート」は
 * {@code BusinessGateway} の話であり、その内側をどう分けるかは別である。
 */
final class BusinessCalls {

    /** 失敗の本文をどこまで載せるか。全部載せると読めない。 */
    private static final int BODY_LIMIT = 200;

    private BusinessCalls() {
    }

    /** 業務 API の失敗に<strong>工程の名前と応答の状態</strong>を添える。 */
    static <T> T call(ScenarioStep step, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            throw new BusinessCallFailedException(
                    step.label() + " が失敗しました（" + describe(e) + "）", e);
        }
    }

    /**
     * 失敗を<strong>切り分けられる形</strong>で述べる。
     *
     * <p>状態だけでは足りない。400 は「入力が違う」としか言わず、どの項目が違うのかは
     * 応答の本文にしかない——実環境で 1 度、契約番号の要る法人で荷主を作ろうとして
     * 400 になり、状態だけでは理由に辿り着けなかった。
     */
    static String describe(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            String body = response.getResponseBodyAsString();
            String detail = body.length() > BODY_LIMIT ? body.substring(0, BODY_LIMIT) : body;
            return detail.isBlank()
                    ? String.valueOf(response.getStatusCode().value())
                    : response.getStatusCode().value() + ": " + detail;
        }
        return e.getMessage();
    }

    /**
     * 前の工程が生んだ識別子を読む。
     *
     * <p><strong>無いまま進めない。</strong>空文字で進めると、存在しない予約に対する
     * 操作が 404 になり、引き継ぎが切れていることが「業務 API の失敗」に化ける。
     */
    static String required(Map<String, String> context, String key) {
        String value = context.get(key);
        if (value == null || value.isBlank()) {
            throw new BusinessCallFailedException(
                    "前の工程が " + key + " を引き継いでいません");
        }
        return value;
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * 引き継がれていれば使い、無ければ既定値へ落とす。
     *
     * <p>乱数が選んだ入力（US37-1）は継続実行だけが持つ。手で押した実行は
     * 持たないため、<strong>既定値へ落とす方が正しい</strong>——ここで断ると、
     * 管理者が押す実行が動かなくなる。
     */
    static String orDefault(Map<String, String> context, String key, String fallback) {
        String value = context.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    static String runId(Map<String, String> context) {
        return context.getOrDefault(BusinessContextKey.RUN_ID, "SIM");
    }
}
