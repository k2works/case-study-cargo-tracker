package com.example.cargotracker.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.gateway.infrastructure.config.JwtAuthenticationFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 後段サービスの API 経路は、Gateway のルートに載り、認証で守られている。
 *
 * <p><b>IT2 では足した経路が未検査でした（レビュー L3）。</b> 経路は増え続けるので、
 * 「足したときに検査する」ではなく「足りていないと赤になる」形にします。</p>
 *
 * <p><b>ルートに載っている経路だけを数えません。</b> 各サービスの
 * {@code @RequestMapping} を全部拾ってから、ルートに載っているかを見ます。載って
 * いるものだけを数える検査は、載せ忘れたものほど漏らします。</p>
 *
 * <p>公開してよい経路は {@link JwtAuthenticationFilter#PUBLIC_PATHS} の 1 か所だけで
 * 決めます。ここに無い経路が認証を通らずに届く形にはしません。</p>
 */
class EveryServiceEndpointIsRoutedAndProtectedTest {

    private static final Pattern REQUEST_MAPPING =
            Pattern.compile("@RequestMapping\\(\"(/api/[^\"]+)\"\\)");
    private static final Pattern ROUTE_PREDICATE =
            Pattern.compile("Path=(/api/[^\\]\\s,]+)");

    private static Path backendRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("settings.gradle.kts が見つかりません");
    }

    private static List<String> serviceEndpoints() throws IOException {
        List<String> endpoints = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(backendRoot())) {
            for (Path file : paths
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .toList()) {
                Matcher matcher = REQUEST_MAPPING.matcher(
                        Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    endpoints.add(matcher.group(1));
                }
            }
        }
        return endpoints;
    }

    private static List<String> gatewayRoutes() throws IOException {
        String config = Files.readString(
                backendRoot().resolve("gatewayms/src/main/resources/application.yml"),
                StandardCharsets.UTF_8);
        List<String> routes = new ArrayList<>();
        Matcher matcher = ROUTE_PREDICATE.matcher(config);
        while (matcher.find()) {
            routes.add(matcher.group(1));
        }
        return routes;
    }

    /** {@code /api/v1/booking/**} の形の予測子に、その経路が当たるか。 */
    private static boolean isCoveredBy(String endpoint, String pattern) {
        String prefix = pattern.endsWith("/**")
                ? pattern.substring(0, pattern.length() - 2) : pattern + "/";
        return (endpoint + "/").startsWith(prefix);
    }

    @Test
    @DisplayName("検査する経路が実際にある（空振りしていない）")
    void thereAreEndpointsToCheck() throws IOException {
        assertThat(serviceEndpoints()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(gatewayRoutes()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("後段サービスの経路はすべて Gateway のルートに載っている")
    void everyEndpointIsRouted() throws IOException {
        List<String> routes = gatewayRoutes();
        List<String> unrouted = serviceEndpoints().stream()
                .filter(endpoint -> routes.stream().noneMatch(r -> isCoveredBy(endpoint, r)))
                .toList();

        assertThat(unrouted)
                .as("Gateway に載っていない経路は、外から届かないか、Gateway を通らずに届く")
                .isEmpty();
    }

    @Test
    @DisplayName("後段サービスの経路は認証で守られている（公開は明示した分だけ）")
    void everyEndpointIsProtected() throws IOException {
        List<String> unprotected = serviceEndpoints().stream()
                .filter(endpoint -> JwtAuthenticationFilter.isPublic(endpoint + "/x"))
                .filter(endpoint -> !endpoint.startsWith("/api/v1/tracking/public"))
                .toList();

        assertThat(unprotected)
                .as("公開してよい経路は PUBLIC_PATHS の 1 か所だけで決める")
                .isEmpty();
    }
}
