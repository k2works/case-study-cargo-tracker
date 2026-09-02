package com.example.gatewayms.security;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

/**
 * 認証不要で通す経路の判定。
 *
 * <p>一覧に載っていない経路は必ず認証を要求する（ADR-004）。名簿方式の検査は
 * 「載っていないものを通す」向きにすると、載せ忘れたものほど無防備になる。
 */
public class PublicPathMatcher {

    private final List<PublicPath> publicPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PublicPathMatcher(List<PublicPath> publicPaths) {
        this.publicPaths = List.copyOf(publicPaths);
    }

    public boolean isPublic(HttpMethod method, String path) {
        return publicPaths.stream()
                .anyMatch(publicPath -> publicPath.method().equals(method)
                        && pathMatcher.match(publicPath.pattern(), path));
    }
}
