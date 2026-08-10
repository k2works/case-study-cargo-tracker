package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * マッパーの SQL が<strong>自分の BC が持つテーブルだけを触る</strong>ことを検証する（C28）。
 *
 * <p><strong>ArchUnit が緑であることは、越境していないことの根拠にならない。</strong>
 * ArchUnit が見るのは Java の依存グラフだけである。マッパーが他 BC のテーブルを
 * SQL で JOIN しても、Java のクラスを 1 つも参照しなければ<strong>どの検査にも映らない</strong>。
 * JIG も同じで、パッケージの依存しか描かない。
 *
 * <p>IT11 で実際にこれが起きた。ArchUnit のルールを 1 件も破らずに、
 * BC 間の結合が SQL の層で 2 本増えた（IT11 レビュー C28）。
 * <strong>「検査が守っているのは何か」を、検査そのものが語れるようにする。</strong>
 *
 * <p><strong>テーブルの所有 BC は {@code data-model.md} が正典である。</strong>
 * ここに書き写した表がずれたら、正典を読み直して直す。
 *
 * <h2>読み取り専用の JOIN をどう扱うか</h2>
 *
 * <p><strong>「読むだけだから」は理由にならない。</strong> Handling のテーブルを変えれば
 * Booking の SQL が黙って壊れる。ADR-012 が「BC を別モジュールに切り出すとき、
 * 動かすのはアダプタだけで済む」と述べた主張は、SQL の越境があると成立しない。
 *
 * <p>ただし<strong>やむを得ず残すもの</strong>は、ここに理由とともに書く。
 * <strong>黙って通すのではなく、名前で残す。</strong>
 */
@DisplayName("マッパーの SQL は自分の BC のテーブルだけを触る（C28）")
class MapperTableOwnershipTest {

    /**
     * テーブルの所有 BC（正典は {@code data-model.md}）。
     *
     * <p>共有のマスタ（{@code location}）と認証（{@code users} / {@code user_roles}）は
     * <strong>どの BC からも読んでよい</strong>。前者は共有カーネルの実体であり
     * （ADR-005）、後者は支援サブドメインとして全 BC の入口に効く（ADR-007）。
     */
    private static final Map<String, String> OWNER = new LinkedHashMap<>();

    /** どの BC からも触ってよいテーブル。 */
    private static final Set<String> SHARED_TABLES = Set.of("location", "users", "user_roles");

    static {
        OWNER.put("cargo", "booking");
        OWNER.put("leg", "booking");
        OWNER.put("booking_notification", "booking");
        OWNER.put("booking_cancellation", "booking");
        OWNER.put("shipper", "shipper");
        OWNER.put("voyage", "routing");
        OWNER.put("carrier_movement", "routing");
        OWNER.put("booking_route_proposal", "routing");
        OWNER.put("proposed_route", "routing");
        OWNER.put("route_candidate", "routing");
        OWNER.put("tracking_activity", "tracking");
        OWNER.put("tracking_handling_event", "tracking");
        OWNER.put("tracking_exception_event", "tracking");
        OWNER.put("handling_activity", "handling");
        OWNER.put("customs_declaration", "handling");
        OWNER.put("customs_status_history", "handling");
        OWNER.put("invoice", "billing");
        OWNER.put("invoice_line_item", "billing");
        OWNER.put("payment", "billing");
        OWNER.put("invoice_reminder", "billing");
        OWNER.put("estimate", "estimation");
    }

    /**
     * <strong>理由を書いたうえで残している越境。</strong>
     *
     * <p><strong>空にできるのが理想である。</strong> ここに行を足すときは、
     * なぜ ACL ポートで運べないのかを書く。<strong>黙って通すのではなく、
     * 名前で残す</strong>ためにこの表がある。
     *
     * <p>IT12 の時点で 8 件ある。**IT11 が足した誤配の現在地（`handling_activity`）は
     * 結果整合の写しに置き換えて解消した**（C28）。残りは 3 つの型に分かれる。
     *
     * <ul>
     *   <li><strong>一覧の表示に要る名前</strong>（荷主名・貨物種別）— 1 件ずつ引くと
     *       一覧の行数だけ問い合わせが増える（N+1）。CQRS の読み取り側として
     *       JOIN で 1 回に収めている</li>
     *   <li><strong>集計</strong>（航海の空き容量）— 予約側の重量を合算する。
     *       ポートで運ぶと全予約を読み出してから足すことになる</li>
     * </ul>
     *
     * <p><strong>「写しにできるもの」は IT13 で返した。</strong> 日程が変わった区間の印（C9）は
     * 航海の更新イベントを Booking が購読して {@code leg} に写す形にした。
     * <strong>次に返す候補として名前を残したものは、名前を消せる形で返す。</strong>
     *
     * <p><strong>この表が長くなるのは設計が緩んだ合図である。</strong>
     * 行を足すたびに、上の 3 つのどれでもないなら、まず ACL ポートを疑う。
     */
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            // --- 一覧の表示に要る名前（N+1 を避けるための読み取り側 JOIN）---
            Map.entry("BookingQueryMapper.java -> shipper",
                    "予約一覧・詳細に出す荷主名。1 件ずつ引くと一覧の行数だけ問い合わせが増える"),
            Map.entry("HandlingMapper.java -> cargo",
                    "荷役一覧に出す貨物種別（US05）。**現物に触る作業員が危険物と気づけないなら"
                            + "申告を登録した意味が半分になる**"),
            Map.entry("CustomsListMapper.java -> cargo",
                    "通関一覧の検索条件（貨物 ID）と荷主名の引き当て（US29）"),
            Map.entry("CustomsListMapper.java -> shipper",
                    "通関一覧に出す荷主名。**連絡が要る仕事の待ち行列**であり、"
                            + "誰の貨物かが読めないと 1 件ずつ予約を開くことになる"),
            // --- 集計 ---
            Map.entry("VoyageMapper.java -> cargo",
                    "航海の空き容量（US08）。割り当て済みの予約の重量を合算する。"
                            + "ポートで運ぶと全予約を読み出してから足すことになる"),
            Map.entry("VoyageMapper.java -> leg",
                    "同上（どの便に割り当たっているかは区間が持つ）"));

    /** {@code FROM} / {@code JOIN} / {@code INTO} / {@code UPDATE} の直後のテーブル名。 */
    private static final Pattern TABLE_REF = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN|INTO|UPDATE)\\s+([a-z_][a-z0-9_]*)");

    /** マッパーのパスから BC 名を取り出す。 */
    private static final Pattern BC_OF_PATH = Pattern.compile(
            "com/example/cargotracker/([a-z]+)/");

    /**
     * <strong>すべてのマッパーが、自分の BC のテーブルだけを触る。</strong>
     *
     * <p>違反があれば<strong>マッパー名・触っているテーブル・その所有者</strong>を
     * すべて並べて落とす。1 件ずつ直すより、全体像が見えたほうが判断しやすい。
     */
    @Test
    void マッパーは自分のBCのテーブルだけを触る() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path mapper : mapperFiles()) {
            String bc = boundedContextOf(mapper);
            String source = Files.readString(mapper);
            for (String table : tablesIn(source)) {
                String owner = OWNER.get(table);
                if (owner == null || owner.equals(bc) || SHARED_TABLES.contains(table)) {
                    continue;
                }
                String key = mapper.getFileName() + " -> " + table;
                if (ALLOWED.containsKey(key)) {
                    continue;
                }
                violations.add("%s（%s）が %s（%s の持ち物）を触っています"
                        .formatted(mapper.getFileName(), bc, table, owner));
            }
        }

        assertThat(violations)
                .as("""
                        マッパーの SQL が他 BC のテーブルを触っています。
                        ACL ポート（application/internal/outboundservices/acl）で運ぶか、
                        やむを得ない場合は ALLOWED に理由とともに書いてください。
                        **ArchUnit はこの越境を検出しません**（Java の依存が生まれないため）。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> 「最小の違反例」だけだと、
     * メタテストが緑でも実コードの違反を見逃す（Flix IT2 で実測 0 件検出の前例）。
     * ここでは<strong>実際にプロジェクトにあった違反の形</strong>
     * （相関サブクエリ・複数 JOIN・別名付き）をそのまま使う。
     */
    @Test
    void 実コードの形の違反を検出できる() {
        String realShapedViolation = """
                @Select(""\"
                        SELECT c.origin_unlocode AS originUnlocode,
                               CASE WHEN c.routing_status = 'MISROUTED' THEN (
                                   SELECT h.location_unlocode
                                     FROM handling_activity h
                                    WHERE h.booking_id = c.booking_id
                                    ORDER BY h.event_completion_time DESC, h.id DESC
                                    LIMIT 1
                               ) END AS misroutedFrom
                          FROM cargo c
                          JOIN shipper s ON s.id = c.shipper_id
                         WHERE c.booking_id = #{bookingId}
                        ""\")
                """;

        Set<String> tables = tablesIn(realShapedViolation);

        assertThat(tables)
                .as("相関サブクエリの中の FROM も、別名付きの JOIN も拾えること")
                .contains("handling_activity", "cargo", "shipper");
    }

    /** <strong>自分の BC のテーブルは違反にしない。</strong> 常に落ちる検査で緑にしない。 */
    @Test
    void 自分のBCのテーブルは違反にしない() {
        assertThat(OWNER.get("cargo")).isEqualTo("booking");
        assertThat(SHARED_TABLES).contains("location");
        // location は共有マスタなので、どの BC から引いても違反にならない
        assertThat(OWNER.containsKey("location")).isFalse();
    }

    private static Set<String> tablesIn(String source) {
        Set<String> tables = new TreeSet<>();
        Matcher matcher = TABLE_REF.matcher(source);
        while (matcher.find()) {
            tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return tables;
    }

    private static String boundedContextOf(Path mapper) {
        Matcher matcher = BC_OF_PATH.matcher(mapper.toString().replace('\\', '/'));
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    private static List<Path> mapperFiles() throws IOException {
        Path root = mainJavaRoot();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.getFileName().toString().endsWith("Mapper.java")).toList();
        }
    }

    /**
     * 本番コードの場所を探す。
     *
     * <p>作業ディレクトリはモジュール直下のことも、リポジトリのルートのこともある。
     * <strong>片方だけを前提にすると、実行の仕方で結果が変わる。</strong>
     */
    private static Path mainJavaRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && current != null; i++) {
            Path candidate = current.resolve("src/main/java/com/example/cargotracker");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            candidate = current.resolve("apps/cargo-tracker/src/main/java/com/example/cargotracker");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("本番コードのディレクトリが見つかりません");
    }
}
