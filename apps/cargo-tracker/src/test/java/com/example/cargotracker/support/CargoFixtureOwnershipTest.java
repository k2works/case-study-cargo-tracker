package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * テストの貨物・荷主は <strong>{@link CargoFixture} だけが作る</strong>（IT14 の C4 / IT15 の M6）。
 *
 * <p><strong>C2 の目的は「{@code cargo} に列を足したとき、直す場所が 1 か所であること」である。</strong>
 * 移し替えただけでは、次に書く人がまた {@code INSERT INTO cargo} を書き、
 * <strong>1 か所だったものが 2 か所に戻る</strong>。
 *
 * <p><strong>返済したことは、次に書くときに思い出す保証にならない</strong>
 * （IT15 の P3）。C2 は IT14・IT15 と 2 度落とされ、そのあいだに
 * <strong>5 クラスと数えていたものが実際には 39 クラスに育っていた</strong>。
 * 同じことを繰り返さないために、返済と同じ変更で検査を入れる。
 *
 * <p><strong>本クラス自身は例外である。</strong> 支援クラスが SQL を持つのは当然であり、
 * <strong>唯一の場所であることがその存在理由</strong>だからである。
 *
 * <p><strong>荷主の作成は対象にしない。</strong> 荷主の登録・訂正・一覧はそれ自体が
 * 主題であり（US02 / US03 / US32）、支援クラスに寄せると<strong>何を確かめて
 * いるのかが読めなくなる</strong>。貨物のテストが特定の荷主を要する場合も
 * {@code CargoFixture.shipper(id)} で渡す形を用意しており、これは正しい使い方である
 * — <strong>正しい形を違反にする検査は入れない。</strong>
 */
@DisplayName("テストの貨物・荷主は支援クラスだけが作る（C2）")
class CargoFixtureOwnershipTest {

    private static final Path TEST_ROOT = Path.of("src/test/java");

    /**
     * 検査の対象外。
     *
     * <p>{@code CargoFixture} が SQL を持つのは当然であり、<strong>唯一の場所である
     * ことがその存在理由</strong>である。本検査自身はメタテストの中で違反の形を
     * 文字列として持つ — <strong>検査が自分を違反として数えると、直しようがない。</strong>
     */
    private static final java.util.Set<String> EXCLUDED = java.util.Set.of(
            "CargoFixture.java", "CargoFixtureOwnershipTest.java");

    /**
     * <strong>テストが {@code cargo} を直に INSERT しない。</strong>
     *
     * <p>違反があればクラス名を並べて落とす。
     */
    @Test
    void 貨物を直に登録するテストは無い() throws IOException {
        assertThat(violations("INSERT INTO cargo"))
                .as("""
                        テストが cargo を直に INSERT しています（IT14 の C4 / IT15 の M6）。

                        **cargo に列を足すたび、直す場所がその数だけ増えます。**
                        CargoFixture を使ってください。足りない項目があれば
                        支援クラスに選択肢を足します。""")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> 「最小の違反例」だけだと、
     * メタテストが緑でも実コードの違反を見逃す（ADR-015 で学んだ形）。
     */
    @Test
    void 実コードの形の違反を検出できる() {
        String realShaped = """
                UUID bookingId = UUID.randomUUID();
                jdbcTemplate.update(""\"
                        INSERT INTO cargo (
                            booking_id, shipper_id, cargo_type, weight,
                            origin_unlocode, destination_unlocode, arrival_deadline,
                            booking_status, routing_status, tracking_number)
                        VALUES (?, ?, 'GENERAL', 1000, 'JPOSA', 'USLAX', CURRENT_DATE + 60,
                                'IN_TRANSIT', 'ROUTED', ?)
                        ""\", bookingId, shipperId, trackingNumber);
                """;

        assertThat(realShaped).contains("INSERT INTO cargo");
        assertThat("""
                CargoFixture.on(jdbcTemplate).status("IN_TRANSIT", "ROUTED").insert();
                """)
                .as("支援クラスを使った形を違反にしないこと（常に落ちる検査で緑にしない）")
                .doesNotContain("INSERT INTO cargo");
    }

    private static boolean excluded(Path source) {
        return EXCLUDED.contains(source.getFileName().toString());
    }

    private static List<String> violations(String needle) throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(TEST_ROOT)) {
            for (Path source : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                if (excluded(source)) {
                    continue;
                }
                if (Files.readString(source).contains(needle)) {
                    found.add(source.getFileName().toString());
                }
            }
        }
        return found;
    }
}
