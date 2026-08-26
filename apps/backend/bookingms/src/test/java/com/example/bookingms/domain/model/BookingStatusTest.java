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
     * <strong>{@code SETTLED} は精算で到達する</strong>（US23・[ADR-028] 決定 1）。
     *
     * <p>IT9 はこの値を置かず、「そこへ動かすコードが無いこと」を検査していた
     * （精算は US23 だったため、値だけ先に置くと「実装済み」と読まれる）。
     * <strong>本 IT でその検査を反転させる。</strong>
     *
     * <p><strong>入金の確認だけがここへ動かす。</strong>引取済からしか来ない
     * ——キャンセルされた予約に「精算済」は無い。
     */
    @Test
    @DisplayName("精算済へは、引取済からだけ進める")
    void advancesIntoSettledOnlyFromDelivered() {
        assertThat(Arrays.stream(BookingStatus.values()).map(Enum::name))
                .as("精算済が無い。入金を確認しても予約が閉じない")
                .contains("SETTLED");

        assertThat(BookingStatus.DELIVERED.canAdvanceTo(BookingStatus.SETTLED)).isTrue();

        // **対で見る。**「引取済から進める」だけを見ると、どこからでも進める実装でも緑になる
        for (BookingStatus from : BookingStatus.values()) {
            if (from != BookingStatus.DELIVERED) {
                assertThat(from.canAdvanceTo(BookingStatus.SETTLED))
                        .as("%s から精算済へ進めている。運んでいない予約が精算済になる", from)
                        .isFalse();
            }
        }
    }

    /**
     * <strong>精算済から先へは動かない。</strong>
     *
     * <p>精算が済んだ予約に荷役が遅れて届いても巻き戻らない——再試行やデッドレターからの
     * 送り直しで、荷役の届く順は入れ替わる。
     */
    @Test
    @DisplayName("精算済からは、どの状態へも進めない")
    void neverAdvancesOutOfSettled() {
        for (BookingStatus next : BookingStatus.values()) {
            assertThat(BookingStatus.SETTLED.canAdvanceTo(next))
                    .as("精算済から %s へ動かせている", next)
                    .isFalse();
        }
    }

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
     * コメントを外す。
     *
     * <p><strong>コメントは検査の対象外である。</strong>「〜へは進めない」と書いた
     * 説明文まで数えると、説明を書いただけで赤になる。
     */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
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
