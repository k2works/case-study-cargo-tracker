package com.example.billingms.infrastructure.outboundservices.notification;

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
 * WireMock 動的ポートで実 HTTP を検証できない問題（IT8 H1）を {@code setPort} 追加で解消する。</p>
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
