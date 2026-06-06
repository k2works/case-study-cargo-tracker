package com.example.trackingms.infrastructure.outboundservices.notification;

import com.sendgrid.Client;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * WireMock 統合テスト用 SendGrid Client サブクラス（IT9 A4.1 / IT8 H1 解消 / US29）。
 *
 * <p>SendGrid SDK の {@link Client#buildUri(String, String, java.util.Map)} は
 * {@code URIBuilder.setHost(baseUri)} で host のみを受理し、port 指定ができないため、
 * WireMock が動的ポートで起動する統合テストで実 HTTP 経路を検証できなかった
 * （IT8 レビュー H1 で持ち越し）。本サブクラスは {@code buildUri} を override して
 * {@link URIBuilder#setPort(int)} を追加することで、WireMock の動的ポートを含む URI を構築する。</p>
 *
 * <p>scheme は WireMock のデフォルトに合わせて常に {@code http} とする
 * （SDK 本体の {@code test} フラグを true 相当で扱う）。</p>
 */
public class WireMockCompatibleSendGridClient extends Client {

    private final int port;

    public WireMockCompatibleSendGridClient(int port) {
        super(true);
        this.port = port;
    }

    @Override
    public URI buildUri(String baseUri, String endpoint, Map<String, String> queryParams)
            throws URISyntaxException {
        URIBuilder builder = new URIBuilder();
        builder.setScheme("http");
        builder.setHost(baseUri);
        builder.setPort(port);
        builder.setPath(endpoint);
        if (queryParams != null) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                builder.setParameter(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }
}
