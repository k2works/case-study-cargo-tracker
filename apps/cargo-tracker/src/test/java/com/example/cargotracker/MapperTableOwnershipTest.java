package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.support.SourceScan;
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
     * <p><strong>正典との一致は {@link #所有表は正典と一致する()} が検査する。</strong>
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
        OWNER.put("tracking_activity", "tracking");
        OWNER.put("tracking_handling_event", "tracking");
        OWNER.put("tracking_exception_event", "tracking");
        OWNER.put("handling_activity", "handling");
        OWNER.put("customs_declaration", "handling");
        OWNER.put("customs_status_history", "handling");
        OWNER.put("handling_correction", "handling");

        OWNER.put("invoice", "billing");
        OWNER.put("invoice_line_item", "billing");
        OWNER.put("payment", "billing");
        OWNER.put("invoice_reminder", "billing");
        OWNER.put("estimate", "estimation");
        // route_candidate は estimate(id) を参照する子テーブルである（IT18 の開始準備で是正）
        OWNER.put("route_candidate", "estimation");
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


    /** 正典（{@code data-model.md}）の「テーブルの所有 BC」表。 */
    private static final String OWNERSHIP_HEADING = "## テーブルの所有 BC";

    /** 表の行（{@code | **Booking** | `cargo` / `leg` |}）。 */
    private static final Pattern OWNERSHIP_ROW = Pattern.compile(
            "^\\|\\s*\\*\\*(.+?)\\*\\*\\s*\\|(.+?)\\|\\s*$", Pattern.MULTILINE);

    /** 行の中のテーブル名（バッククォートで囲まれたもの）。 */
    private static final Pattern TABLE_NAME = Pattern.compile("`([a-z_][a-z0-9_]*)`");

    /** 所有者を持たない（共有）と宣言している行の見出し。 */
    private static final String SHARED_HEADING = "共有";

    /**
     * 正典から読んだテーブルの所有 BC。
     *
     * <p><strong>書き写さずに引用する。</strong> 正典が変われば、ここが変わる。
     */
    private static Map<String, String> ownershipFromDataModel() throws IOException {
        Map<String, String> canon = new LinkedHashMap<>();
        forEachOwnershipRow((heading, tables) -> {
            if (heading.startsWith(SHARED_HEADING)) {
                return;
            }
            tables.forEach(table -> canon.put(table, heading.toLowerCase(Locale.ROOT)));
        });
        return canon;
    }

    /** 正典が「所有者を持たない」と宣言しているテーブル。 */
    private static Set<String> sharedTablesFromDataModel() throws IOException {
        Set<String> shared = new TreeSet<>();
        forEachOwnershipRow((heading, tables) -> {
            if (heading.startsWith(SHARED_HEADING)) {
                shared.addAll(tables);
            }
        });
        return shared;
    }

    /** 所有表の各行に見出し（BC 名）とテーブル名を渡す。 */
    private static void forEachOwnershipRow(java.util.function.BiConsumer<String, List<String>> row)
            throws IOException {
        String document = Files.readString(dataModelDocument());
        int from = document.indexOf(OWNERSHIP_HEADING);
        if (from < 0) {
            throw new AssertionError(
                    "正典に「" + OWNERSHIP_HEADING + "」の節がありません。検査は何も見ていません");
        }
        // 次の見出しまでを表の範囲とする（後続の節の表を巻き込まない）
        int to = document.indexOf("\n## ", from + OWNERSHIP_HEADING.length());
        String section = document.substring(from, to < 0 ? document.length() : to);

        Matcher rows = OWNERSHIP_ROW.matcher(section);
        while (rows.find()) {
            List<String> tables = new ArrayList<>();
            Matcher names = TABLE_NAME.matcher(rows.group(2));
            while (names.find()) {
                tables.add(names.group(1));
            }
            row.accept(rows.group(1).trim(), tables);
        }
    }

    /** 正典の場所（作業ディレクトリがモジュール直下でもリポジトリのルートでもよい）。 */
    private static Path dataModelDocument() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && current != null; i++) {
            Path candidate = current.resolve("docs/design/data-model.md");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("正典（docs/design/data-model.md）が見つかりません");
    }

    /** {@code FROM} / {@code JOIN} / {@code INTO} / {@code UPDATE} の直後のテーブル名。 */
    private static final Pattern TABLE_REF = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN|INTO|UPDATE)\\s+([a-z_][a-z0-9_]*)");

    /** マッパーのパスから BC 名を取り出す。 */
    private static final Pattern BC_OF_PATH = Pattern.compile(
            "com/example/cargotracker/([a-z]+)/");

    /**
     * <strong>名簿は正典（{@code data-model.md}）と一字一句そろっている</strong>（IT17 の R2）。
     *
     * <p><strong>書き写した表は、正典が変わっても追随しない。</strong> 本クラスの
     * {@code OWNER} は {@code data-model.md} の「テーブルの所有 BC」を書き写したもので、
     * 「ずれたら正典を読み直して直す」という<strong>人の注意力だけが同期の担保</strong>
     * だった。ADR-015 で「正典は data-model.md」と宣言しながら、
     * <strong>正典と検査が食い違っても何も起きなかった。</strong>
     *
     * <p>そこで<strong>正典を読んで突き合わせる</strong>。以後、表に行を足して名簿に
     * 足し忘れれば（またはその逆で）ここが赤くなる。
     */
    @Test
    void 所有表は正典と一致する() throws IOException {
        Map<String, String> canon = ownershipFromDataModel();

        assertThat(canon)
                .as("正典から 1 件も読めないなら、検査は何も見ていない")
                .isNotEmpty();
        assertThat(canon)
                .as("""
                        テーブルの所有 BC が正典（data-model.md）とずれています。

                        **正典は data-model.md です。**名簿（OWNER）を正典に合わせてください。
                        表に行を足したなら名簿にも足し、消したなら名簿からも消します。""")
                .containsExactlyInAnyOrderEntriesOf(OWNER);
    }

    /**
     * <strong>共有テーブルも正典と一致する。</strong>
     *
     * <p>共有は「どの BC からも読んでよい」という<strong>最も緩い扱い</strong>である。
     * ここが黙って増えると、越境が越境として映らなくなる。
     */
    @Test
    void 共有テーブルは正典と一致する() throws IOException {
        assertThat(sharedTablesFromDataModel())
                .as("共有テーブル（どの BC からも読んでよいもの）が正典とずれています")
                .containsExactlyInAnyOrderElementsOf(SHARED_TABLES);
    }

    /**
     * <strong>すべてのマッパーが、自分の BC のテーブルだけを触る。</strong>
     *
     * <p>違反があれば<strong>マッパー名・触っているテーブル・その所有者</strong>を
     * すべて並べて落とす。1 件ずつ直すより、全体像が見えたほうが判断しやすい。
     */
    @Test
    void マッパーは自分のBCのテーブルだけを触る() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> unregistered = new ArrayList<>();
        for (Path mapper : mapperFiles()) {
            String bc = boundedContextOf(mapper);
            String source = Files.readString(mapper);
            for (String table : tablesIn(source)) {
                if (SHARED_TABLES.contains(table)) {
                    continue;
                }
                String owner = OWNER.get(table);
                if (owner == null) {
                    // **知らないテーブルを素通りさせない**（IT14 レビュー C8）。
                    // 所有者を書かなければ「越境していない」ことにできてしまい、
                    // **表に載せ忘れた新しいテーブルほど検査から漏れる**。
                    // 実際に `handling_correction` が 3 イテレーション素通りしていた
                    unregistered.add("%s（%s）が触る %s の所有者が表にありません"
                            .formatted(mapper.getFileName(), bc, table));
                    continue;
                }
                if (owner.equals(bc)) {
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

        assertThat(unregistered)
                .as("""
                        所有者の分からないテーブルを触っています（ADR-015）。
                        OWNER に所有 BC を書いてください（正典は data-model.md）。
                        **書かないと「越境していない」ことにできてしまいます。**""")
                .isEmpty();

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

    /**
     * <strong>名簿に無いテーブルを素通りさせない</strong>（IT16 の T4）。
     *
     * <p><strong>名簿方式は反転した性質を持つ。</strong> 載っているものは検査されるが、
     * <strong>載せ忘れたものほど検査から漏れる</strong>。{@code handling_correction} が
     * IT12 から 3 イテレーション素通りしていたのはこの形である。
     *
     * <p>未登録を弾く判定そのものは IT14 レビュー C8 で入っている。
     * <strong>ここで確かめるのは「入れたこと」ではなく「働くこと」である。</strong>
     *
     * <p>あわせて<strong>名簿がスキーマに追随しているか</strong>を見る。
     * 上の検査は「マッパーが触ったテーブル」しか見ないため、
     * <strong>まだ誰も引いていない新しいテーブルは名簿に無くても気づけない</strong>。
     * マイグレーションの側から突き合わせることで、載せ忘れをその場で見つける。
     */
    @Test
    void 名簿に無いテーブルは所有者が引けない() throws IOException {
        assertThat(OWNER.get("存在しないテーブル"))
                .as("**未登録は素通りではなく「分からない」でなければならない**")
                .isNull();
        assertThat(SHARED_TABLES).doesNotContain("存在しないテーブル");

        Set<String> declared = new TreeSet<>(OWNER.keySet());
        declared.addAll(SHARED_TABLES);
        Set<String> missing = new TreeSet<>(createdTables());
        missing.removeAll(declared);

        assertThat(missing)
                .as("""
                        マイグレーションが作ったテーブルが名簿にありません（ADR-015）。

                        **載せ忘れたものほど検査から漏れます。**
                        OWNER に所有 BC を書いてください（正典は data-model.md の所有表）。""")
                .isEmpty();
    }

    /** マイグレーションが作ったテーブル名。 */
    private static Set<String> createdTables() throws IOException {
        Pattern create = Pattern.compile(
                "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([a-z_][a-z0-9_]*)");
        Set<String> tables = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(Path.of("src/main/resources/db/migration"))) {
            for (Path sql : paths.filter(p -> p.toString().endsWith(".sql")).toList()) {
                Matcher matcher = create.matcher(Files.readString(sql));
                while (matcher.find()) {
                    tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        // Flyway 自身の管理表は業務のテーブルではない
        tables.remove("flyway_schema_history");
        return tables;
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

    /**
     * 走査対象。<strong>空なら検査は何も見ていない</strong>。
     *
     * <p>走査の根が変わったときに<strong>静かに 0 件になり、緑のまま何も検査しない</strong>
     * 形を防ぐ（R8 で {@link SourceScan} へ寄せた際に歯止めを足した）。
     */
    private static List<Path> mapperFiles() {
        List<Path> found = mapperFilesScan();
        if (found.isEmpty()) {
            throw new AssertionError("Mapper が 1 つも見つかりません。検査は何も見ていません");
        }
        return found;
    }

    private static List<Path> mapperFilesScan() {
        return SourceScan.main().filesEndingWith("Mapper.java");
    }

}
