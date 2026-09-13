package com.example.cargotracker.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 後段サービスの経路と Gateway のルートを、<b>ソースと設定から集める</b>。
 *
 * <p><b>{@code EveryServiceEndpointIsRoutedAndProtectedTest} から分けた。</b>
 * 1 ファイルが 500 行を超えると、何を確かめているファイルなのかが読めなくなる
 * （行数の基準はそのための目安）。<b>集めることと突き合わせることは別の責務</b>で、
 * 切り口もそこに置いた。</p>
 *
 * <p>名簿を手書きしない——<b>実物を読む</b>。手書きにすると、足した経路が
 * 名乗り出ないまま検査を素通りする。</p>
 */
final class BackendEndpoints {

    private BackendEndpoints() {
    }

    /**
     * 経路になりうる注釈を全部拾う。
     *
     * <p>{@code @RequestMapping("...")} の形だけを拾うと、
     * {@code @RequestMapping(value = "...")} やクラスレベル注釈を持たない
     * {@code @GetMapping("/api/...")} は「存在しないこと」になり、
     * ルートに載っていなくても緑になる。対象になりうるものを全部拾ってから、
     * 書き方を見る。</p>
     */
    static final Pattern ANY_MAPPING = Pattern.compile(
            "@(?:Request|Get|Post|Put|Delete|Patch)Mapping\\s*\\([^)]*?\"(/[^\"]*)\"");

    /**
     * クラスに付いた {@code @RequestMapping}。メソッド側は
     * {@code @PutMapping("/{bookingId}")} のように<b>相対で書かれる</b>ので、
     * ここを前置きにしないと経路として復元できない。
     *
     * <p><b>これが無い間、検査は method 側の経路を 1 本も見ていなかった。</b>
     * {@code /api/} を含む文字列だけを拾っていたため、相対のものは「存在しない
     * こと」になり、ルートにも宣言にも無いまま緑になる。</p>
     */
    static final Pattern CLASS_MAPPING = Pattern.compile(
            "@RequestMapping\\s*\\([^)]*?\"(/api/[^\"]+)\"");
    static final Pattern ROUTE_PREDICATE =
            Pattern.compile("Path=(/api/[^\\]\\s,]+)");

    static Path backendRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("settings.gradle.kts が見つかりません");
    }

    static List<String> serviceEndpoints() throws IOException {
        List<String> endpoints = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(backendRoot())) {
            for (Path file : paths
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    // 名前で絞らない。*Controller.java 以外に書かれた経路は
                    // 「存在しないこと」になり、検査を素通りする。
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher classMapping = CLASS_MAPPING.matcher(source);
                String prefix = classMapping.find() ? classMapping.group(1) : null;
                Matcher matcher = ANY_MAPPING.matcher(source);
                while (matcher.find()) {
                    String path = matcher.group(1);
                    if (path.startsWith("/api/")) {
                        endpoints.addAll(expand(path));
                    } else if (prefix != null) {
                        // 相対の経路を前置きと繋ぐ。"/" だけの指定は前置きそのもの。
                        endpoints.addAll(expand("/".equals(path) ? prefix : prefix + path));
                    }
                }
            }
        }
        return endpoints;
    }

    /**
     * サービスごとに違う接頭辞を、<b>実際に通る経路へ展開する</b>。
     *
     * <p>共有の読み口（退避一覧）は 1 クラスで 5 サービスに載るので、経路を
     * {@code ${cargo.context}} から組み立てている。<b>そのまま突き合わせると
     * プレースホルダのまま「載っていない」になり、逆に無視すると 5 本とも
     * 検査されない</b>——展開して、1 本ずつ見る。</p>
     *
     * <p>相手は各サービスの {@code application.yml} の {@code cargo.context}
     * である。<b>名簿を手書きしない</b>（サービスを足したら、そのファイルが増える）。</p>
     */
    static List<String> expand(String path) throws IOException {
        if (!path.contains("${cargo.context}")) {
            return List.of(path);
        }
        List<String> expanded = new ArrayList<>();
        try (Stream<Path> files = Files.walk(backendRoot())) {
            for (Path yaml : files
                    .filter(p -> p.toString().replace('\\', '/')
                            .endsWith("/src/main/resources/application.yml"))
                    .toList()) {
                Matcher context = CARGO_CONTEXT.matcher(
                        Files.readString(yaml, StandardCharsets.UTF_8));
                if (context.find()) {
                    expanded.add(path.replace("${cargo.context}", context.group(1)));
                }
            }
        }
        assertThat(expanded)
                .as("${cargo.context} を宣言したサービスが 1 つも無いなら、経路は誰にも届かない")
                .isNotEmpty();
        return expanded;
    }

    /** {@code cargo.context: booking} の宣言。 */
    static final Pattern CARGO_CONTEXT =
            Pattern.compile("(?m)^cargo:\\s*\\n\\s+context:\\s*(\\S+)");

    static List<String> gatewayRoutes() throws IOException {
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
}
