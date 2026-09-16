package com.example.cargotracker.archfixture.violating.infrastructure.acl;

import org.springframework.web.client.RestTemplate;

/** 違反フィクスチャ: ACL が HTTP でサービス間を呼ぶ（配送経路は Axon Server 一本）。 */
public class RestTemplateRouteFinder {

    private final RestTemplate restTemplate = new RestTemplate();

    public String find(String origin) {
        return restTemplate.getForObject("http://routingms/routes?origin=" + origin, String.class);
    }
}
