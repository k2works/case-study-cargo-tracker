package com.example.cargotracker.simulation.infrastructure.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;

/**
 * Gateway の応答を読む（[ADR-0020] 決定 2）。
 *
 * <p><b>読み方を 1 か所にする。</b> 断りの言い回しも本文の読み取りも、工程ごとに
 * 書くと片方だけが正しくなる。</p>
 */
final class GatewayResponses {

    /**
     * 別サービスの断りを包む言い回し。
     *
     * <p><b>間の字面を決め打ちしない。</b> 実測では {@code failed.\nCaused by }
     * という改行を挟む形だった——「{@code failed: }」で切ろうとして 2 度外した
     * （IT16）。区切りの前後の空白も改行も版で変わる。</p>
     */
    private static final Pattern WRAPPER =
            Pattern.compile("(?s).*(?:Caused by|failed:)\\s*");

    private static final ObjectMapper JSON = new ObjectMapper();

    private GatewayResponses() {
    }

    /**
     * 本文を読む。
     *
     * <p><b>読めなければ空として扱う。</b> 断られた応答が JSON とは限らず、
     * ここで例外にすると「どう断られたか」が失われる。</p>
     */
    static JsonNode parse(GatewayCalls.Response response) {
        try {
            return JSON.readTree(response.body() == null ? "{}" : response.body());
        } catch (java.io.IOException e) {
            return JSON.createObjectNode();
        }
    }

    /**
     * 断りの理由のうち、人が読む部分を取り出す。
     *
     * <p><b>内部の言葉をそのまま出さない。</b> 経路の問い合わせは別サービスへ
     * 渡るので、断りが「An exception was thrown by the remote message handling
     * component: Handling query with identifier [...] failed: 〜」という形で
     * 包まれて返る（IT16 のクラスタで実測）。<b>読む人が要るのは最後の一節</b>
     * ——「その港を通る航海が登録されていません: AQMCM」である。</p>
     */
    static String businessReason(GatewayCalls.Response response) {
        String message = parse(response).path("message").asText(null);
        if (message == null || message.isBlank()) {
            return response.body();
        }
        return WRAPPER.matcher(message).replaceFirst("").trim();
    }

    /**
     * 旅程の指紋。<b>航海番号と港の並び</b>で作る。
     *
     * <p>時刻は入れない——同じ便でも読むたびに揺れうる値を混ぜると、
     * 「変わった」が信用できなくなる（US35 §受入基準 3）。</p>
     */
    static String fingerprintOf(JsonNode legs) {
        StringBuilder text = new StringBuilder();
        for (JsonNode leg : legs) {
            text.append(leg.path("voyageNumber").asText()).append('>')
                    .append(leg.path("loadUnLocode").asText()).append('-')
                    .append(leg.path("unloadUnLocode").asText()).append('|');
        }
        return text.toString();
    }
}
