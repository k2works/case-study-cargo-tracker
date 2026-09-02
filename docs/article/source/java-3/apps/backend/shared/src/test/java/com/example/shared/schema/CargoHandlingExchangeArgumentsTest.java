package com.example.shared.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 荷役の交換機を、宣言する全サービスが<strong>同じ引数</strong>で宣言していることを検査する
 * （[ADR-025] 決定 1）。
 *
 * <p><strong>なぜ検査にするか。</strong>交換機の引数は<strong>既存の環境では宣言し直せない</strong>。
 * 1 つのサービスが違う引数で宣言すると {@code PRECONDITION_FAILED} で落ち、
 * <strong>後続のキュー宣言まで止まる</strong>——そのサービスは起動しない。
 *
 * <p><strong>Testcontainers では出ない。</strong>テストは毎回まっさらなブローカーを立てる
 * ため、最初に宣言した引数がそのまま通る。食い違いが出るのは<strong>すでに交換機がある
 * 環境</strong>——kind やステージングであり、そこで初めて分かる。
 *
 * <p>対象は名簿で持たない。{@code cargoHandlingChannel} を宣言しているサービスを、
 * 合成ルートの実体から検出する（{@link LocationSeedReplicaTest} と同じ立場）。
 */
class CargoHandlingExchangeArgumentsTest {

    private static final Path BACKEND_ROOT = Path.of("..").toAbsolutePath().normalize();

    /** 荷役の交換機の名前。契約（testFixtures）と同じ値である。 */
    private static final String EXCHANGE = "cargoHandlingChannel";

    /**
     * 荷役の交換機を宣言しうるサービス。
     *
     * <p><strong>ここに載っていないサービスが宣言し始めたら検出できない。</strong>
     * それを避けるため、{@link #findsTheDeclarations()} が「2 つ以上見つかること」を
     * 先に確かめる——名簿が腐ったら、まずそこで気づく。
     */
    private static final java.util.List<String> SERVICES =
            java.util.List.of("bookingms", "handlingms", "trackingms");

    /** {@code new TopicExchange(...HANDLING_EXCHANGE..., Map.of(...))} の引数部分を取る。 */
    private static final Pattern DECLARATION = Pattern.compile(
            "new\\s+TopicExchange\\s*\\(([^,]+),(.*?)\\);", Pattern.DOTALL);

    @Test
    @DisplayName("荷役の交換機を宣言しているサービスが 3 つある（集まらないまま緑にしない）")
    void findsTheDeclarations() {
        assertThat(declarations())
                .as("交換機の宣言が集まっていない。検査が何も守らないまま緑になる")
                .containsOnlyKeys("bookingms", "handlingms", "trackingms");
    }

    /**
     * <strong>引数まで一致していること</strong>を見る。
     *
     * <p>名前だけ合わせても、{@code alternate-exchange} が片方に無ければ落ちる。
     */
    @Test
    @DisplayName("荷役の交換機の宣言が、全サービスで一致している")
    void everyServiceDeclaresTheSameArguments() {
        Map<String, String> declarations = declarations();

        assertThat(declarations.values().stream().distinct().toList())
                .as("交換機の宣言が食い違っている: %s。"
                        + "既存の環境では宣言し直せず、PRECONDITION_FAILED で後続のキュー宣言まで止まる",
                        declarations)
                .hasSize(1);
    }

    /**
     * サービス名 → 宣言の引数（定数を値へ解決し、空白を詰めたもの）。
     *
     * <p><strong>定数名ではなく値で比べる。</strong>サービスごとに定数の置き場も名前も
     * 違う（handlingms は自分の {@code EXCHANGE}、購読側は {@code HANDLING_EXCHANGE}）。
     * 名前で比べると、同じ値を宣言していても食い違いに見える——実際、最初にそう書いて
     * 誤検出した。
     */
    private static Map<String, String> declarations() {
        Map<String, String> found = new LinkedHashMap<>();
        for (String service : SERVICES) {
            Path main = BACKEND_ROOT.resolve(service).resolve("src/main/java");
            if (!Files.isDirectory(main)) {
                continue;
            }
            Map<String, String> constants = constantsOf(main);
            configFiles(main).forEach(file -> {
                Matcher matcher = DECLARATION.matcher(read(file));
                while (matcher.find()) {
                    String name = resolve(matcher.group(1).trim(), constants);
                    if (name.contains(EXCHANGE)) {
                        found.put(service, normalize(resolve(matcher.group(2), constants)));
                    }
                }
            });
        }
        return found;
    }

    /** そのサービスが持つ {@code public static final String} の定数（名前 → 値）。 */
    private static Map<String, String> constantsOf(Path main) {
        Map<String, String> constants = new LinkedHashMap<>();
        Pattern constant = Pattern.compile(
                "static\\s+final\\s+String\\s+([A-Z_]+)\\s*=\\s*\"([^\"]*)\"");
        try (Stream<Path> files = Files.walk(main)) {
            files.filter(path -> path.getFileName().toString().endsWith("Channels.java"))
                    .forEach(file -> {
                        Matcher matcher = constant.matcher(read(file));
                        while (matcher.find()) {
                            constants.put(matcher.group(1), matcher.group(2));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + main, e);
        }
        return constants;
    }

    /** 式の中の定数参照を値へ置き換える。 */
    private static String resolve(String expression, Map<String, String> constants) {
        String resolved = expression;
        for (Map.Entry<String, String> constant : constants.entrySet()) {
            resolved = resolved.replaceAll("[A-Za-z]*\\." + constant.getKey() + "\\b",
                    '"' + constant.getValue() + '"');
            resolved = resolved.replaceAll("(?<![.\\w])" + constant.getKey() + "\\b",
                    '"' + constant.getValue() + '"');
        }
        return resolved;
    }

    private static Stream<Path> configFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.getFileName().toString().endsWith("Config.java"))
                    .toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + root, e);
        }
    }

    /** 空白と改行を詰める。整形の違いを食い違いと読まない。 */
    private static String normalize(String arguments) {
        return arguments.replaceAll("\\s+", "");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
