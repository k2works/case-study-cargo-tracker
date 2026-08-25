package com.example.gatewayms;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;

/**
 * <strong>業務 API の入口が、すべて Gateway から振り分けられている</strong>ことを検査する。
 *
 * <p><strong>ここに足し忘れると、画面からは 404 に見える。</strong>Gateway がどこへも
 * 振らないだけで、サービス側は正しく動いている——サービスのテストは全部緑のまま、
 * 実環境でだけ落ちる。IT9 で {@code /api/v1/cancellations} が実際にこれを踏んだ。
 *
 * <p><strong>名簿を書き写さない。</strong>各サービスのコントローラが宣言している経路を
 * 読み取り、Gateway の {@code Path} 述語で覆えるかを見る。写しにすると、写し忘れが
 * そのまま「検査も知らない経路」になる。
 */
@DisplayName("Gateway の振り分け")
class GatewayRouteCoverageTest {

    private static final Path BACKEND_ROOT = Path.of("..").toAbsolutePath().normalize();

    /** コントローラのクラス・メソッドに書かれた {@code "/api/v1/..."} を拾う。 */
    private static final Pattern API_PATH = Pattern.compile("\"(/api/v1/[^\"{]*)");

    /**
     * <strong>プロファイルごとに見る。</strong>まとめて集めると、片方に書いてあるだけで
     * 覆われたことになる——本番だけ 404 になる形を見逃す（最初にそう書いて、
     * 壊しても赤にならなかった）。
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"application.yml", "application-product.yml"})
    @DisplayName("業務 API の入口が、すべて Gateway の振り分けに載っている")
    void everyApiPathIsRouted(String profile) {
        List<String> predicates = predicates(profile);
        assertThat(predicates)
                .as("Gateway の Path 述語が読めていない。検査が何も守らないまま緑になる")
                .isNotEmpty();

        List<String> uncovered = new ArrayList<>();
        for (String path : declaredApiPaths()) {
            if (predicates.stream().noneMatch(predicate -> covers(predicate, path))) {
                uncovered.add(path);
            }
        }

        assertThat(uncovered)
                .as("%s で、Gateway がどこへも振らない業務 API がある。画面からは 404 に見え、"
                        + "サービス側のテストは全部緑のままになる", profile)
                .isEmpty();
    }

    /**
     * 述語が経路を覆うか。
     *
     * <p>{@code /api/v1/bookings/**} は自身（{@code /api/v1/bookings}）も覆う——
     * Spring Cloud Gateway の {@code **} は 0 セグメントにも一致する。
     */
    private static boolean covers(String predicate, String path) {
        String base = predicate.endsWith("/**")
                ? predicate.substring(0, predicate.length() - 3)
                : predicate;
        return path.equals(base) || path.startsWith(base + "/");
    }

    /** 各サービスのコントローラが宣言している業務 API の経路。 */
    private static Set<String> declaredApiPaths() {
        Set<String> paths = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(BACKEND_ROOT)) {
            files.filter(file -> file.getFileName().toString().endsWith("Controller.java"))
                    .filter(file -> file.toString().contains("/src/main/java/"))
                    .filter(file -> !file.toString().contains("/gatewayms/"))
                    .forEach(file -> {
                        Matcher matcher = API_PATH.matcher(read(file));
                        while (matcher.find()) {
                            paths.add(trimTrailingSlash(matcher.group(1)));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + BACKEND_ROOT, e);
        }
        return paths;
    }

    private static String trimTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
    }

    /** そのプロファイルに書かれた {@code Path} 述語。 */
    private static List<String> predicates(String profile) {
        List<String> predicates = new ArrayList<>();
        Path file = BACKEND_ROOT.resolve("gatewayms/src/main/resources").resolve(profile);
        assertThat(Files.isRegularFile(file))
                .as("%s が無い。検査の対象が消えている", profile)
                .isTrue();
        Matcher matcher = Pattern.compile("- Path=(\\S+)").matcher(read(file));
        while (matcher.find()) {
            for (String path : matcher.group(1).split(",")) {
                predicates.add(path.trim());
            }
        }
        return predicates;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
