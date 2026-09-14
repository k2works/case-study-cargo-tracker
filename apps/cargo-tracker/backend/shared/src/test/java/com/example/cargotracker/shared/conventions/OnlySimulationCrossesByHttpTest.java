package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * サービスを越える HTTP の呼び出しは {@code simulationms} だけ（[ADR-0020] 決定 2）。
 *
 * <p><b>本システムの越境は契約イベント・コマンド・クエリである。</b> HTTP で他の
 * サービスを呼ぶ本番コードは 1 件も無い（IT16 の着手前に実測）。`simulationms` は
 * <b>あえてその例外</b>になる——人が操作するのと同じ経路（Gateway・認可つき）を
 * 踏むことが目的だからである。</p>
 *
 * <p><b>名簿で緩めない。</b> 「許す 1 つ」を足すのではなく、<b>全サービスを走査して
 * から</b>判定する。名簿方式にすると、次に HTTP を使い始めたサービスが素通りする
 * （IT15 で認可の名簿が同じ形で漏れた）。</p>
 */
class OnlySimulationCrossesByHttpTest {

    /** HTTP クライアント。**`simulationms` だけが使ってよい**。 */
    private static final List<String> HTTP_CLIENTS = List.of(
            "RestClient", "WebClient", "RestTemplate", "HttpClient");

    /** 例外として許すサービス。[ADR-0020] 決定 2。 */
    private static final String ALLOWED = "simulationms";

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

    @Test
    @DisplayName("[ADR-0020] HTTP でサービスを越えるのは simulationms だけ")
    void onlySimulationUsesHttpClients() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> paths = Files.walk(backendRoot())) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .toList()) {
                scanned++;
                String normalized = path.toString().replace('\\', '/');
                if (normalized.contains("/" + ALLOWED + "/")) {
                    continue;
                }
                String source = Files.readString(path, StandardCharsets.UTF_8);
                for (String client : HTTP_CLIENTS) {
                    // import で判定する。名前が本文のコメントに出るだけでは越境しない。
                    if (source.contains("import org.springframework.web.client." + client + ";")
                            || source.contains("import java.net.http." + client + ";")) {
                        offenders.add(path.getFileName() + " → " + client);
                    }
                }
            }
        }

        assertThat(scanned)
                .as("本番のソースを 1 つも読めていない（検査が空振りしている）")
                .isGreaterThan(100);
        assertThat(offenders)
                .as("**越境は契約イベント・コマンド・クエリで行う。** HTTP で他の"
                        + "サービスを呼んでよいのは %s だけである（[ADR-0020] 決定 2）", ALLOWED)
                .isEmpty();
    }
}
