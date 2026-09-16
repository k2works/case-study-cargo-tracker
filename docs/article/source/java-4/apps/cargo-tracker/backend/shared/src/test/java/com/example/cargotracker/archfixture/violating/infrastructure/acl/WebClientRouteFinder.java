package com.example.cargotracker.archfixture.violating.infrastructure.acl;

import java.net.http.HttpClient;

/** 違反フィクスチャ: 名簿方式では素通りする HTTP クライアント（RestTemplate 以外）。 */
public class WebClientRouteFinder {

    private final HttpClient client = HttpClient.newHttpClient();

    public String describe() {
        return client.toString();
    }
}
