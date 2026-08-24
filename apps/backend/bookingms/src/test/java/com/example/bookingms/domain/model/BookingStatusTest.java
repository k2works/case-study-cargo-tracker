package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 予約の状態（US30・[ADR-025]）。
 *
 * <p>IT9 で輸送中・配送完了・キャンセルが加わる。輸送中はキャンセルに承認が要る境目
 * であり、<strong>状態が無いと「承認が要るかどうか」を判断できない</strong>。
 */
@DisplayName("予約の状態")
class BookingStatusTest {

    /** ソースの置き場。**遷移の呼び出し箇所を数える**ために読む。 */
    private static final Path DOMAIN =
            Path.of("src/main/java/com/example/bookingms/domain/model");

    @Test
    @DisplayName("輸送中・配送完了・キャンセルを持つ")
    void hasTheStatusesIntroducedByUs30() {
        assertThat(Arrays.stream(BookingStatus.values()).map(Enum::name))
                .contains("IN_TRANSIT", "DELIVERED", "CANCELLED");
    }

    /**
     * <strong>{@code SETTLED} へ進める経路を作らない</strong>（IT9 計画タスク 4.4）。
     *
     * <p>精算は US23（IT12）である。値だけ先に置くと、「精算まで実装済み」と読まれる。
     * <strong>遷移の呼び出し箇所を数える</strong>——値の有無ではなく、そこへ動かす
     * コードが無いことを見る（[ADR-024] 決定 8 と同じ形）。
     *
     * <p>この検査は、{@code SETTLED} を足したうえで遷移を書いた瞬間に赤になる。
     */
    @Test
    @DisplayName("SETTLED へ進める経路は無い")
    void hasNoTransitionIntoSettled() {
        assertThat(Arrays.stream(BookingStatus.values()).map(Enum::name))
                .as("SETTLED を足している。値だけ先に置くと「精算まで実装済み」と読まれる")
                .doesNotContain("SETTLED");

        List<String> mentions = sourceFileNames()
                .filter(name -> stripComments(read(DOMAIN.resolve(name))).contains("SETTLED"))
                .toList();

        assertThat(mentions)
                .as("SETTLED へ動かすコードがある。精算は US23（IT12）であり、"
                        + "この IT では経路を作らない")
                .isEmpty();
    }

    /**
     * コメントを落とす。
     *
     * <p><strong>コメントは検査の対象外である。</strong>「SETTLED へは進めない」と
     * 説明を書いた瞬間に赤になる検査は、説明を書くことを罰する——書けなくなると、
     * なぜそうなっているかが誰にも読めなくなる。見たいのは<strong>コード</strong>である。
     */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\n]*", "");
    }

    /**
     * <strong>キャンセルは、承認を経る道以外から起きない</strong>（US30-3）。
     *
     * <p>{@code CANCELLED} へ動かす箇所が集約の 1 メソッドに限られていることを見る。
     * 別の場所から直接動かせると、輸送中の貨物が承認を経ずに止まる。
     */
    @Test
    @DisplayName("キャンセルへ動かす箇所は、集約の 1 か所だけ")
    void cancelsOnlyThroughTheAggregate() {
        List<String> files = sourceFileNames()
                .filter(BookingStatusTest::movesIntoCancelled)
                .toList();

        assertThat(files)
                .as("キャンセルへ動かす箇所が増えている。承認を迂回する経路ができる")
                .containsExactly("Cargo.java");
    }

    /**
     * その状態へ<strong>動かしている</strong>か。
     *
     * <p><strong>参照と遷移を区別する。</strong>「キャンセルなら断る」と<em>判定</em>する
     * コードは、キャンセルへ<em>動かして</em>いない。区別せずに数えると、判定を足した
     * だけで赤になり、検査が邪魔になって外される。
     *
     * <p>見るのは新しい状態の組み立て（{@code new CargoStatus(BookingStatus.CANCELLED}）である。
     */
    private static boolean movesIntoCancelled(String name) {
        return stripComments(read(DOMAIN.resolve(name)))
                .contains("new CargoStatus(BookingStatus.CANCELLED");
    }

    private static Stream<String> sourceFiles() {
        return sourceFileNames().map(name -> read(DOMAIN.resolve(name)));
    }

    private static Stream<String> sourceFileNames() {
        try (Stream<Path> files = Files.list(DOMAIN)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("ドメイン層を読めない: " + DOMAIN, e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
