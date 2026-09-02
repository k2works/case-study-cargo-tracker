package com.example.bookingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * メールアドレスの受け入れ（[ADR-012]）。
 *
 * <p>確かめているのは<strong>重複判定と連絡に使えること</strong>だけである。厳密な妥当性は
 * 送信してみるまで分からない。
 */
@DisplayName("メールアドレス")
class EmailAddressTest {

    @Nested
    @DisplayName("受け入れる形")
    class Accepted {

        @ParameterizedTest
        @ValueSource(strings = {
            "sales@example.com",
            "a@b.c",
            "shipper.tokyo@marubeni.co.jp",
            "user+tag@example.com",
            // ドットが続く形は緩い検査では通す。厳密さを求めると、実在するアドレスを弾く
            "a@b..c",
        })
        @DisplayName("空白がなく、@ の後ろにドットがあれば受け入れる")
        void accepts(String value) {
            assertThatCode(() -> EmailAddress.of(value)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("断る形")
    class Rejected {

        @ParameterizedTest
        @ValueSource(strings = {
            "",
            "@example.com",       // @ の前が空
            "sales@",             // @ の後ろが空
            "sales@example",      // ドットが無い
            "sales@.com",         // ドットの前が空
            "sales@example.",     // ドットの後ろが空
            "sales example@a.com", // 空白を含む
            "a@b@c.com",          // @ が 2 つ
        })
        @DisplayName("連絡に使えない形は断る")
        void rejects(String value) {
            assertThatThrownBy(() -> EmailAddress.of(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null は断る")
        void rejectsNull() {
            assertThatThrownBy(() -> EmailAddress.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * <strong>外部からの入力で、入口の仕事を跳ね上げられない。</strong>
     *
     * <p>元の実装は正規表現（{@code [^@\s]+@[^@\s]+\.[^@\s]+}）で、ドメイン側の
     * {@code [^@\s]} が {@code .} を含むため区切りの取り方が一意に決まらなかった。
     * 一致しない長い入力では区切りを総当たりし、長さの 2 乗に比例する時間がかかる。
     *
     * <p><strong>時間そのものを測らない。</strong>経過時間の判定は機械の都合で揺れるうえ、
     * 脆い実装に戻しても環境次第で緑になる。ここでは<strong>長さの上限</strong>を固定する
     * ——上限があれば、どんな入力でも仕事は有限で頭打ちになる。
     */
    @Nested
    @DisplayName("外部からの入力に縛りを掛ける")
    class Bounded {

        @Test
        @DisplayName("上限（RFC 5321 の 254 文字）を超えるアドレスは断る")
        void rejectsOverlongInput() {
            String local = "a".repeat(250);

            assertThatThrownBy(() -> EmailAddress.of(local + "@b.co"))
                    .as("長さを縛らないと、長さに比例した仕事が入口の数だけ積み上がる")
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("上限ちょうどは受け入れる")
        void acceptsExactlyAtTheLimit() {
            String value = "a".repeat(254 - "@b.co".length()) + "@b.co";
            assertThat(value).hasSize(254);

            assertThatCode(() -> EmailAddress.of(value)).doesNotThrowAnyException();
        }

        /**
         * 区切りの総当たりが起きる形を、上限の内側で流す。
         *
         * <p>上限があるので総当たりでも有限だが、<strong>再帰でループを回す実装</strong>
         * （{@code (?:\.[^@\s.]+)+} のような正規表現）に変えると
         * {@code StackOverflowError} になりうる。{@code Error} は例外の検査をすり抜けるため、
         * 明示的に「落ちないこと」を見る。
         */
        @Test
        @DisplayName("区切りが曖昧な入力でも、落ちずに断る")
        void handlesAmbiguousInputWithoutBlowingUp() {
            String evil = "a@" + "b.".repeat(120);
            assertThat(evil.length()).isLessThanOrEqualTo(254);

            assertThatCode(() -> {
                try {
                    EmailAddress.of(evil);
                } catch (IllegalArgumentException _) {
                    // 断るのは正しい。ここで見たいのは Error で落ちないこと
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("復元では長さを検査しない。上限が無かったころの行が読めなくなる")
        void restoreDoesNotApplyTheLimit() {
            String stored = "a".repeat(300) + "@example.com";

            assertThat(EmailAddress.restore(stored).value()).isEqualTo(stored);
        }
    }

    @Test
    @DisplayName("記録と画面に出す文字列は、アドレスそのもの")
    void printsItsValue() {
        assertThat(EmailAddress.of("sales@example.com")).hasToString("sales@example.com");
    }

    @Test
    @DisplayName("同じアドレスは等しい。重複判定がこれに乗る")
    void equalsByValue() {
        assertThat(EmailAddress.of("a@b.co")).isEqualTo(EmailAddress.of("a@b.co"));
    }
}
