package com.example.shared.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 地点マスタの複製がサービス間でずれていないことを検査する（ADR-014）。
 *
 * <p>複製は「同じ内容を人が複数箇所に書く」形になるため必ずずれる。ずれた側のサービスだけが
 * ある地点を扱えなくなり、しかも症状は「その地点を使ったときだけ」出る。テストデータが
 * ずれた地点を踏んでいなければ CI は緑のまま本番で落ちる。
 *
 * <p>検査対象は名簿で持たない。{@code location} テーブルを作っているサービスを
 * マイグレーションの実体から検出する。名簿にすると、載せ忘れたサービスほど無検査になる。
 */
class LocationSeedReplicaTest {

    /** 地点マスタの正（ADR-010 の決定 3）。 */
    private static final String MASTER = "bookingms";

    private static final Path BACKEND_ROOT = Path.of("..").toAbsolutePath().normalize();

    /** {@code CREATE TABLE location (...)} の検出。 */
    private static final Pattern CREATES_LOCATION =
            Pattern.compile("CREATE\\s+TABLE\\s+(IF\\s+NOT\\s+EXISTS\\s+)?location\\b", Pattern.CASE_INSENSITIVE);

    /** {@code INSERT INTO location (...) VALUES ...;} の本体を取り出す。 */
    private static final Pattern LOCATION_SEED = Pattern.compile(
            "INSERT\\s+INTO\\s+location\\s*\\(([^)]*)\\)\\s*VALUES(.*?);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    @DisplayName("location を持つ全サービスの種データが正と一致する")
    void everyReplicaMatchesTheMaster() throws IOException {
        Map<String, String> seeds = seedsByService();

        assertThat(seeds)
                .as("location を作っているサービスが 1 つも検出できていない。この検査は何も守っていない")
                .isNotEmpty();
        assertThat(seeds)
                .as("正である %s の種データが見つからない".formatted(MASTER))
                .containsKey(MASTER);

        String master = seeds.get(MASTER);
        List<String> drifted = new ArrayList<>();
        for (Map.Entry<String, String> entry : seeds.entrySet()) {
            if (!entry.getKey().equals(MASTER) && !entry.getValue().equals(master)) {
                drifted.add(entry.getKey());
            }
        }

        assertThat(drifted)
                .as("地点マスタの複製が %s とずれているサービス（ADR-014）".formatted(MASTER))
                .isEmpty();
    }

    /** サービス名 → 正規化した地点の種データ。location を作っていないサービスは含まない。 */
    private Map<String, String> seedsByService() throws IOException {
        Map<String, String> seeds = new LinkedHashMap<>();
        for (String service : services()) {
            String migrations = concatenatedMigrations(service);
            if (!CREATES_LOCATION.matcher(migrations).find()) {
                continue;
            }
            seeds.put(service, normalizedSeed(migrations));
        }
        return seeds;
    }

    private String concatenatedMigrations(String service) throws IOException {
        Path dir = BACKEND_ROOT.resolve(service).resolve("src/main/resources/db/migration");
        if (!Files.isDirectory(dir)) {
            return "";
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> sorted = files.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
            StringBuilder all = new StringBuilder();
            for (Path file : sorted) {
                all.append(Files.readString(file)).append('\n');
            }
            return all.toString();
        }
    }

    /**
     * 種データを比較可能な形に整える。
     *
     * <p>並び順・空白・コメントの違いで落ちても、それは業務上のずれではない。
     * 一方で列の顔ぶれ・地点の顔ぶれ・各列の値のずれは業務に効くため落とす。
     */
    private String normalizedSeed(String migrations) {
        List<String> rows = new ArrayList<>();
        Matcher matcher = LOCATION_SEED.matcher(stripComments(migrations));
        while (matcher.find()) {
            String columns = collapse(matcher.group(1)).toLowerCase(Locale.ROOT);
            for (String row : collapse(matcher.group(2)).split("(?<=\\)),")) {
                rows.add(columns + " => " + collapse(row));
            }
        }
        return String.join("\n", rows.stream().sorted().toList());
    }

    private String stripComments(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }

    private String collapse(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private List<String> services() throws IOException {
        String settings = Files.readString(BACKEND_ROOT.resolve("settings.gradle"));
        Matcher matcher = Pattern.compile("^include\\s+'([^']+)'", Pattern.MULTILINE).matcher(settings);
        List<String> services = new ArrayList<>();
        while (matcher.find()) {
            services.add(matcher.group(1));
        }
        return services;
    }
}
