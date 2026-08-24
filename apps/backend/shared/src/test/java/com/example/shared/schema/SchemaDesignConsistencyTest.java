package com.example.shared.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 設計（{@code docs/design/data-model.md}）と実スキーマ（Flyway マイグレーション）が
 * ずれていないことを検査する。
 *
 * <p><strong>なぜ検査にするか。</strong> IT7・IT8 は計画の「注」に「設計へ反映する」と列挙して
 * 約束したが、IT8 のクローズ時点で 13 件中 12 件が未反映だった。列挙は守られたかどうかを
 * 誰も判定しないため、書いた本人も含めて「直したつもり」になる。ずれは
 * <strong>設計だけを読んで実装する人</strong>に効く——存在しない列を前提にした SQL は、
 * 実行して初めて落ちる。
 *
 * <p><strong>何を正とするか。</strong> 実スキーマを正とする。設計は実装を説明する文書であり、
 * 動いているものに合わせる。ただし<strong>本検査はどちらが正かを決めない</strong>——
 * 食い違いを見せるだけであり、直す向きは人が判断する。
 *
 * <p><strong>対象は名簿で持たない。</strong> 設計の論理データモデル（PlantUML の entity ブロック）
 * を機械的に読み、{@code <接頭辞>_db} からサービス名 {@code <接頭辞>ms} を導く。名簿にすると、
 * 載せ忘れたテーブルほど無検査になる（{@link LocationSeedReplicaTest} と同じ立場）。
 *
 * <p><strong>監査カラムは対象外。</strong> {@code created_at} / {@code updated_at} は
 * data-model.md の「設計上の判断 7」で全テーブルに付けると宣言されており、
 * ER 図には意図的に書かれていない。これを差分として出すと、実体のない指摘で表が埋まる。
 */
class SchemaDesignConsistencyTest {

    private static final Path BACKEND_ROOT = Path.of("..").toAbsolutePath().normalize();

    private static final Path DATA_MODEL = BACKEND_ROOT.resolve("../../docs/design/data-model.md").normalize();

    /** 全テーブルに付ける監査カラム（設計上の判断 7）。ER 図には書かれない。 */
    private static final Set<String> AUDIT_COLUMNS = Set.of("created_at", "updated_at");

    /** {@code ### booking_db — Booking Context} のような節見出しから DB 名を取る。 */
    private static final Pattern DB_SECTION = Pattern.compile("^###\\s+(\\w+_db)\\b");

    /** {@code entity "handling_activity\n（荷役作業記録）" as handling_activity {} の開始行。 */
    private static final Pattern ENTITY_START = Pattern.compile("^entity\\s+\"([a-z_][a-z0-9_]*)");

    /** {@code * booking_id : VARCHAR(20) <<NOT NULL>>} のようなカラム行。 */
    private static final Pattern ENTITY_COLUMN =
            Pattern.compile("^\\*?\\s*([a-z_][a-z0-9_]*)\\s*:\\s*([^<]+?)\\s*(<<.*)?$");

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)\\s*\\((.*?)\\)\\s*;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ALTER_TABLE = Pattern.compile(
            "ALTER\\s+TABLE\\s+([a-z_][a-z0-9_]*)\\s+(.*?);", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 列定義の先頭でないもの（表制約）を落とすためのキーワード。 */
    private static final Set<String> TABLE_CONSTRAINTS =
            Set.of("PRIMARY", "UNIQUE", "FOREIGN", "CHECK", "CONSTRAINT", "EXCLUDE");


    /**
     * 設計にあるが<strong>まだ作っていない</strong>もの。値は「いつ作るか」。
     *
     * <p>この一覧は<strong>約束ではなく検査対象</strong>である。{@link #pendingListDoesNotRot()} が
     * 「一覧に載っているのに実在する」ものを落とすため、実装した瞬間にこの行を消さないと赤になる。
     * IT7・IT8 の「注に列挙して約束する」形が守られなかったのは、列挙を誰も判定しなかったからで、
     * 一覧そのものが悪いのではない。<strong>減る向きにしか動かせない</strong>ことが要る。
     */
    private static final Map<String, String> PENDING_TABLES = Map.of(
            "booking_db.estimate", "US01 輸送見積（未着手）",
            "booking_db.route_candidate", "US01 輸送見積（未着手）",
            "billing_db.invoice", "US21-US23・IT11-IT12",
            "billing_db.invoice_line_item", "US21-US23・IT11-IT12",
            "billing_db.payment", "US21-US23・IT11-IT12");

    /** 設計にあるが、その列を使うストーリーがまだ来ていないもの。扱いは {@link #PENDING_TABLES} と同じ。 */
    private static final Map<String, String> PENDING_COLUMNS = Map.of(
            "booking_db.cargo.declared_value", "US21 料金算出・IT11",
            "booking_db.cargo.origin_unlocode", "US28 誤配再設計・IT10",
            "booking_db.cargo.consignee_name", "US16 引取・IT10",
            "booking_db.cargo.consignee_email", "US16 引取・IT10",
            "booking_db.cargo.last_known_location_unlocode", "US28 誤配検知・IT10",
            "booking_db.cargo.current_voyage_number", "US28 誤配検知・IT10",
            "booking_db.cargo.last_handling_event_type", "US28 誤配検知・IT10",
            "booking_db.cargo.last_handling_event_location", "US28 誤配検知・IT10",
            "booking_db.cargo.last_handling_event_voyage", "US28 誤配検知・IT10");

    // --- 検査 -------------------------------------------------------------

    @Test
    @DisplayName("検査対象を実体から集められている（集まらないまま緑にしない）")
    void findsSomethingToCheck() {
        Map<String, Map<String, Map<String, String>>> design = designSchema();

        assertThat(design)
                .as("data-model.md の論理データモデルから DB が 1 つも読めていない。パーサが壊れている")
                .isNotEmpty();
        assertThat(design.values().stream().mapToInt(Map::size).sum())
                .as("テーブルが 1 つも読めていない。パーサが壊れている")
                .isGreaterThan(10);
        assertThat(design.keySet()).contains("tracking_db", "handling_db", "booking_db");
    }

    @Test
    @DisplayName("設計にあるテーブルがマイグレーションに存在する")
    void everyDesignedTableExists() {
        List<String> missing = new ArrayList<>();

        design().forEach((db, tables) -> {
            Map<String, Map<String, String>> actual = migrationSchema(serviceOf(db));
            tables.keySet().stream()
                    .filter(table -> !actual.containsKey(table))
                    .filter(table -> !PENDING_TABLES.containsKey(db + "." + table))
                    .forEach(table -> missing.add("%s（%s）に %s が無い".formatted(serviceOf(db), db, table)));
        });

        assertThat(missing)
                .as("data-model.md にあるテーブルがマイグレーションに無い。"
                        + "設計だけを読んで実装すると、存在しない表への SQL を書くことになる")
                .isEmpty();
    }

    @Test
    @DisplayName("設計とマイグレーションのカラムが一致する（名前と型）")
    void everyColumnMatches() {
        List<String> drift = new ArrayList<>();

        design().forEach((db, tables) -> {
            Map<String, Map<String, String>> actual = migrationSchema(serviceOf(db));
            tables.forEach((table, designed) -> {
                Map<String, String> real = actual.get(table);
                if (real == null) {
                    return; // 表そのものの欠落は everyDesignedTableExists が指す
                }
                designed.forEach((column, type) -> {
                    if (!real.containsKey(column)) {
                        if (!PENDING_COLUMNS.containsKey("%s.%s.%s".formatted(db, table, column))) {
                            drift.add("%s.%s: 設計にある %s がマイグレーションに無い".formatted(db, table, column));
                        }
                    } else if (!real.get(column).equals(type)) {
                        drift.add("%s.%s.%s: 設計 %s / 実体 %s".formatted(db, table, column, type, real.get(column)));
                    }
                });
                real.keySet().stream()
                        .filter(column -> !designed.containsKey(column))
                        .filter(column -> !AUDIT_COLUMNS.contains(column))
                        .forEach(column ->
                                drift.add("%s.%s: 実体にある %s が設計に無い".formatted(db, table, column)));
            });
        });

        assertThat(drift)
                .as("data-model.md と Flyway マイグレーションが食い違っている。"
                        + "設計を読んで実装する人に効く欠陥であり、実行して初めて落ちる")
                .isEmpty();
    }


    @Test
    @DisplayName("未実装の一覧が腐っていない（実装したら消えている）")
    void pendingListDoesNotRot() {
        List<String> stale = new ArrayList<>();
        Map<String, Map<String, Map<String, String>>> design = design();

        PENDING_TABLES.forEach((key, when) -> {
            String db = key.substring(0, key.indexOf('.'));
            String table = key.substring(key.indexOf('.') + 1);
            if (migrationSchema(serviceOf(db)).containsKey(table)) {
                stale.add("%s は実装済み（%s）。PENDING_TABLES から消す".formatted(key, when));
            }
            if (!design.getOrDefault(db, Map.of()).containsKey(table)) {
                stale.add("%s は設計にもう無い。PENDING_TABLES から消す".formatted(key));
            }
        });

        PENDING_COLUMNS.forEach((key, when) -> {
            String[] parts = key.split("\\.");
            Map<String, String> real = migrationSchema(serviceOf(parts[0])).get(parts[1]);
            if (real != null && real.containsKey(parts[2])) {
                stale.add("%s は実装済み（%s）。PENDING_COLUMNS から消す".formatted(key, when));
            }
        });

        assertThat(stale)
                .as("未実装の一覧は減る向きにしか動かせない。"
                        + "実装したのに載ったままなら、この一覧はもう何も守っていない")
                .isEmpty();
    }

    // --- 設計の読み取り ---------------------------------------------------

    private static Map<String, Map<String, Map<String, String>>> design() {
        return designSchema();
    }

    /** DB 名 → テーブル名 → カラム名 → 正規化した型。 */
    private static Map<String, Map<String, Map<String, String>>> designSchema() {
        List<String> lines;
        try {
            lines = Files.readAllLines(DATA_MODEL);
        } catch (IOException e) {
            throw new UncheckedIOException("data-model.md を読めない: " + DATA_MODEL, e);
        }

        Map<String, Map<String, Map<String, String>>> schema = new LinkedHashMap<>();
        String currentDb = null;
        Map<String, String> currentTable = null;

        for (String raw : lines) {
            String line = raw.trim();

            Matcher section = DB_SECTION.matcher(line);
            if (section.find()) {
                currentDb = section.group(1);
                currentTable = null;
                continue;
            }
            if (currentDb == null) {
                continue;
            }

            Matcher entity = ENTITY_START.matcher(line);
            if (entity.find()) {
                currentTable = new LinkedHashMap<>();
                schema.computeIfAbsent(currentDb, key -> new LinkedHashMap<>())
                        .put(entity.group(1), currentTable);
                continue;
            }
            if (currentTable == null) {
                continue;
            }
            if (line.equals("}")) {
                currentTable = null;
                continue;
            }
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }

            Matcher column = ENTITY_COLUMN.matcher(line);
            if (column.matches()) {
                currentTable.put(column.group(1), normalizeType(column.group(2)));
            }
        }
        return schema;
    }

    /** {@code booking_db} → {@code bookingms}。名簿を持たない。 */
    private static String serviceOf(String db) {
        return db.substring(0, db.length() - "_db".length()) + "ms";
    }

    // --- マイグレーションの読み取り ---------------------------------------

    /** テーブル名 → カラム名 → 正規化した型。マイグレーションを順に適用した結果。 */
    private static Map<String, Map<String, String>> migrationSchema(String service) {
        Map<String, Map<String, String>> schema = new LinkedHashMap<>();
        Path dir = BACKEND_ROOT.resolve(service).resolve("src/main/resources/db/migration");
        if (!Files.isDirectory(dir)) {
            return schema;
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".sql")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("マイグレーションを読めない: " + dir, e);
        }

        for (Path file : files) {
            apply(schema, stripComments(read(file)));
        }
        return schema;
    }

    private static void apply(Map<String, Map<String, String>> schema, String sql) {
        Matcher create = CREATE_TABLE.matcher(sql);
        while (create.find()) {
            schema.put(create.group(1).toLowerCase(Locale.ROOT), parseColumns(create.group(2)));
        }

        Matcher alter = ALTER_TABLE.matcher(sql);
        while (alter.find()) {
            Map<String, String> table = schema.get(alter.group(1).toLowerCase(Locale.ROOT));
            if (table != null) {
                applyAlteration(table, alter.group(2).trim());
            }
        }
    }

    private static void applyAlteration(Map<String, String> table, String action) {
        if (action.toUpperCase(Locale.ROOT).matches("^ADD\\s+(CONSTRAINT|PRIMARY|UNIQUE|FOREIGN|CHECK)\\b.*")) {
            return; // 表制約であって列ではない
        }
        Matcher add = Pattern.compile(
                        "^ADD\\s+(?:COLUMN\\s+)?([a-z_][a-z0-9_]*)\\s+(.*)$",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(action);
        if (add.find()) {
            table.put(add.group(1).toLowerCase(Locale.ROOT), leadingType(add.group(2)));
            return;
        }
        Matcher rename = Pattern.compile(
                        "^RENAME\\s+COLUMN\\s+([a-z_][a-z0-9_]*)\\s+TO\\s+([a-z_][a-z0-9_]*)",
                        Pattern.CASE_INSENSITIVE)
                .matcher(action);
        if (rename.find()) {
            String type = table.remove(rename.group(1).toLowerCase(Locale.ROOT));
            if (type != null) {
                table.put(rename.group(2).toLowerCase(Locale.ROOT), type);
            }
            return;
        }
        Matcher drop = Pattern.compile(
                        "^DROP\\s+(?:COLUMN\\s+)?([a-z_][a-z0-9_]*)", Pattern.CASE_INSENSITIVE)
                .matcher(action);
        if (drop.find()) {
            table.remove(drop.group(1).toLowerCase(Locale.ROOT));
        }
    }

    private static Map<String, String> parseColumns(String body) {
        Map<String, String> columns = new LinkedHashMap<>();
        for (String definition : splitTopLevel(body)) {
            String trimmed = definition.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+", 2);
            if (tokens.length < 2 || TABLE_CONSTRAINTS.contains(tokens[0].toUpperCase(Locale.ROOT))) {
                continue;
            }
            columns.put(tokens[0].toLowerCase(Locale.ROOT), leadingType(tokens[1]));
        }
        return columns;
    }

    /** 括弧の深さを見て、型の {@code VARCHAR(20)} を区切りと誤らないようにする。 */
    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : body.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    /** 列定義の残りから型だけを取り出し、正規化する。 */
    private static String leadingType(String rest) {
        String normalized = rest.replaceAll("\\s+", " ").trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.startsWith("TIMESTAMP WITH TIME ZONE") || upper.startsWith("TIMESTAMPTZ")) {
            return "TIMESTAMP WITH TIME ZONE";
        }
        Matcher head = Pattern.compile("^([A-Za-z ]+?(?:\\s*\\([^)]*\\))?)(?:\\s|$)").matcher(normalized);
        return normalizeType(head.find() ? head.group(1) : normalized);
    }

    private static String normalizeType(String type) {
        String normalized = type.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BIGSERIAL" -> "BIGINT";
            case "SERIAL" -> "INTEGER";
            case "TIMESTAMPTZ" -> "TIMESTAMP WITH TIME ZONE";
            case "INT" -> "INTEGER";
            case "BOOL" -> "BOOLEAN";
            default -> normalized;
        };
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }

    /** {@code --} 行コメントを落とす。コメントの中の {@code CREATE TABLE} を拾わないため。 */
    private static String stripComments(String sql) {
        return sql.replaceAll("(?m)--[^\\n]*", "");
    }
}
