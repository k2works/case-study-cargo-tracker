package com.example.gatewayms.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

/**
 * 認証まわりの設定（application.yml の app 配下）。
 *
 * @param publicPaths 認証不要で通す経路。ここに載っていない経路はすべて認証を要求する
 * @param jwt JWT の設定
 */
@ConfigurationProperties(prefix = "app")
public record GatewaySecurityProperties(List<PublicPathProperty> publicPaths, Jwt jwt) {

    public record PublicPathProperty(HttpMethod method, String pattern) {
    }

    public record Jwt(String secret) {
    }

    public List<PublicPath> toPublicPaths() {
        return publicPaths.stream()
                .map(property -> new PublicPath(property.method(), property.pattern()))
                .toList();
    }
}
