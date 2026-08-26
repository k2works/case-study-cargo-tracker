package com.example.shared.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 相手サービスの所在が、デプロイの手順で必ず渡されることを検査する。
 *
 * <p><strong>同じ形の欠陥を 2 度踏んだ。</strong>IT5 は bookingms → routingms、
 * IT12 は bookingms → billingms。どちらも<strong>既定値が開発機（localhost）を指す</strong>
 * ため、クラスタの中では自分自身を指す——<strong>入れ忘れても起動は成功し</strong>、
 * その相手を使う操作だけが失敗する。実環境の往復を通すまで誰も気づかなかった。
 *
 * <p>ここでは、各サービスの {@code application.yml} が読む
 * {@code APP_*_SERVICE_BASE_URL} が、k8s のマニフェストに<strong>すべて</strong>
 * 現れることを確かめる。名簿は持たない——設定の実体から集める。
 */
@DisplayName("相手サービスの所在")
class ServiceEndpointConfigTest {

    private static final Path BACKEND_ROOT = Path.of("..").toAbsolutePath().normalize();

    private static final Path MANIFESTS =
            BACKEND_ROOT.resolve("../../ops/k8s/kustomize/base").normalize();

    /** {@code ${APP_XXX_SERVICE_BASE_URL:...}} の検出。 */
    private static final Pattern ENDPOINT_VARIABLE =
            Pattern.compile("\\$\\{(APP_[A-Z_]*SERVICE_BASE_URL)");

    @Test
    @DisplayName("設定が読む相手の所在は、すべて k8s マニフェストで渡している")
    void everyServiceEndpointIsProvidedByTheManifests() throws IOException {
        Map<String, List<String>> required = requiredVariablesByService();

        assertThat(required)
                .as("相手の所在を読む設定が 1 つも見つからない場合、この検査は何も守らない")
                .isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : required.entrySet()) {
            String manifest = readManifest(entry.getKey());
            for (String variable : entry.getValue()) {
                if (!manifest.contains(variable)) {
                    missing.add("%s: %s をマニフェストで渡していない".formatted(
                            entry.getKey(), variable));
                }
            }
        }

        assertThat(missing)
                .as("既定値は開発機（localhost）を指す。クラスタの中では自分自身を指すため、"
                        + "入れ忘れても起動は成功し、その相手を使う操作だけが失敗する")
                .isEmpty();
    }

    /** サービス名 → そのサービスが読む相手の所在の環境変数。 */
    private Map<String, List<String>> requiredVariablesByService() throws IOException {
        Map<String, List<String>> required = new LinkedHashMap<>();
        for (String service : services()) {
            Path config = BACKEND_ROOT.resolve(service)
                    .resolve("src/main/resources/application.yml");
            if (!Files.isRegularFile(config)) {
                continue;
            }
            List<String> variables = new ArrayList<>();
            Matcher matcher = ENDPOINT_VARIABLE.matcher(Files.readString(config));
            while (matcher.find()) {
                variables.add(matcher.group(1));
            }
            if (!variables.isEmpty()) {
                required.put(service, variables);
            }
        }
        return required;
    }

    private String readManifest(String service) throws IOException {
        Path manifest = MANIFESTS.resolve(service + ".yaml");
        return Files.isRegularFile(manifest) ? Files.readString(manifest) : "";
    }

    private List<String> services() throws IOException {
        try (Stream<Path> directories = Files.list(BACKEND_ROOT)) {
            return directories.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith("ms"))
                    .sorted()
                    .toList();
        }
    }
}
