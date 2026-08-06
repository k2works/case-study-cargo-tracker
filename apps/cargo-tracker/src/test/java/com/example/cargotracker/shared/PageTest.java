package com.example.cargotracker.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.application.paging.Page;
import com.example.cargotracker.shared.application.paging.PageRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 一覧のページ送りを検証する。
 *
 * <p>一覧は貨物予約・荷主・航路の 3 つがあり、**同じ計算を 3 か所に書くと必ずずれる**。
 * 境界（最終ページ・0 件・ページ番号の範囲外）はどれも「たまにしか出ないが出ると壊れる」
 * ため、値オブジェクトとしてユニットテストで固定する。
 */
class PageTest {

    @Test
    void 既定のページは1ページ目で20件() {
        PageRequest request = PageRequest.of(null);

        assertThat(request.pageNumber()).isEqualTo(1);
        assertThat(request.size()).isEqualTo(20);
        assertThat(request.offset()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "2, 20", "5, 80"})
    void ページ番号からオフセットを計算する(int pageNumber, int expectedOffset) {
        assertThat(PageRequest.of(pageNumber).offset()).isEqualTo(expectedOffset);
    }

    /**
     * 範囲外のページ番号は 1 ページ目として扱う。
     *
     * <p>**URL を直接編集しただけで 500 やマイナスのオフセットにしない。**
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void ページ番号が1未満なら1ページ目として扱う(int pageNumber) {
        assertThat(PageRequest.of(pageNumber).pageNumber()).isEqualTo(1);
    }

    @Test
    void 総件数からページ数を求める() {
        assertThat(Page.of(List.of("a"), PageRequest.of(1), 41).totalPages()).isEqualTo(3);
    }

    /** 境界。**20 件ちょうどで 2 ページ目を作らない。** */
    @ParameterizedTest
    @CsvSource({"0, 1", "1, 1", "20, 1", "21, 2", "40, 2", "41, 3"})
    void ページ数の境界(long totalItems, int expectedPages) {
        assertThat(Page.of(List.of(), PageRequest.of(1), totalItems).totalPages())
                .isEqualTo(expectedPages);
    }

    /** 0 件でもページ数は 1。**「0 ページ目」を画面に出さない。** */
    @Test
    void 件数が0でもページ数は1() {
        Page<String> page = Page.of(List.of(), PageRequest.of(1), 0);

        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.isEmpty()).isTrue();
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void 最初のページには前が無く最後のページには次が無い() {
        Page<String> first = Page.of(List.of("a"), PageRequest.of(1), 41);
        Page<String> middle = Page.of(List.of("b"), PageRequest.of(2), 41);
        Page<String> last = Page.of(List.of("c"), PageRequest.of(3), 41);

        assertThat(first.hasPrevious()).isFalse();
        assertThat(first.hasNext()).isTrue();
        assertThat(middle.hasPrevious()).isTrue();
        assertThat(middle.hasNext()).isTrue();
        assertThat(last.hasPrevious()).isTrue();
        assertThat(last.hasNext())
                .as("最終ページで「次へ」が押せると、空のページに飛ぶ")
                .isFalse();
    }

    /** 総件数を超えたページ番号でも「次へ」は出ない。 */
    @Test
    void 総件数を超えたページには次が無い() {
        Page<String> page = Page.of(List.of(), PageRequest.of(99), 41);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void 中身はそのまま保持する() {
        assertThat(Page.of(List.of("a", "b"), PageRequest.of(1), 2).items())
                .containsExactly("a", "b");
    }

    @Test
    void 負の総件数を拒否する() {
        assertThatThrownBy(() -> Page.of(List.of(), PageRequest.of(1), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
