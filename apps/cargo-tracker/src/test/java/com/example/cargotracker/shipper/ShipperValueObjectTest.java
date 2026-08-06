package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.cargotracker.shipper.domain.model.Address;
import com.example.cargotracker.shipper.domain.model.Email;
import com.example.cargotracker.shipper.domain.model.Phone;
import com.example.cargotracker.shipper.domain.model.ShipperCode;
import com.example.cargotracker.shipper.domain.model.ShipperName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 荷主コンテキストの値オブジェクトの不変条件。
 *
 * <p><strong>境界値はユニットで固定する。</strong> 統合テストからしか触れていないと、
 * 通った 1 本の値だけが検証され、境界の反対側は誰も確かめていない状態になる。
 *
 * <p>とくに <strong>画面の検証（Bean Validation）とドメインの検証は別物である</strong>点に注意する。
 * 画面をすり抜けた値がドメインで例外になれば、利用者には 500 として見える。
 */
// テストは @Nested の内側にある。SonarQube は親クラスの @Test だけを数えるため
// 「テストが無い」と判定するが、実際には親クラス経由ですべて実行される。
// **入れ子をやめると、値オブジェクトごとの区切りが失われる。** 構造を優先する。
@SuppressWarnings("java:S2187")
class ShipperValueObjectTest {

    @Nested
    class メールアドレス {

        @ParameterizedTest
        @ValueSource(strings = {"a@example.com", "user.name+tag@example.co.jp"})
        void 正しい形式を受け入れる(String value) {
            assertThatCode(() -> new Email(value)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {
            "no-at-mark",
            // **Bean Validation の @Email は通すが、ドメインは通さない。**
            // 画面をすり抜けるとドメインで例外になり、利用者には 500 に見える
            "a@b",
            "a b@example.com",
            "a@@example.com",
            // 連続したドットはメールアドレスとして正しくない
            "a@b..com",
            "a@.com",
            "a@example.",
        })
        void 不正な形式を拒否する(String value) {
            assertThatThrownBy(() -> new Email(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * 上限を超える入力は、正規表現に渡る<strong>前に</strong>落ちる。
         *
         * <p>形式の検査を先に行うと、数万文字の入力がそのまま照合にかかる。
         * 後戻りの多い正規表現では、入力欄に長い文字列を貼るだけで CPU を
         * 消費させられる（ReDoS。SonarQube java:S5852）。
         *
         * <p><strong>時間では判定しない。</strong> 実測したところ、順序を戻しても
         * この入力では目に見える差が出ず、テストは緑のまま通った。
         * 代わりに**どちらの検査で落ちたか**をメッセージで見る。これなら
         * 順序が入れ替わった瞬間に落ちる。
         */
        @Test
        void 上限を超える入力は形式の検査より先に落ちる() {
            String attack = "a@" + "b".repeat(100_000);

            assertThatThrownBy(() -> new Email(attack))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("文字以内")
                    .as("形式の検査が先に走ると「形式が不正です」になり、"
                            + "上限を超える入力が照合にかかっている")
                    .hasMessageNotContaining("形式が不正");
        }

        @Test
        void 長さの境界を守る() {
            String local = "a".repeat(200 - "@example.com".length());
            assertThatCode(() -> new Email(local + "@example.com")).doesNotThrowAnyException();
            assertThatThrownBy(() -> new Email("a" + local + "@example.com"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 住所 {

        private static Address address(String country, String postalCode, String region,
                String city, String street) {
            return new Address(country, postalCode, region, city, street);
        }

        @Test
        void 番地以外がそろっていれば成立する() {
            assertThatCode(() -> address("JP", "100-0001", "東京都", "千代田区", null))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void 国が空なら拒否する(String value) {
            assertThatThrownBy(() -> address(value, "100-0001", "東京都", "千代田区", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"J", "JPN"})
        void 国コードは2文字でなければならない(String value) {
            // 画面の @Size(min=2,max=2) をすり抜けた値がここで止まる
            assertThatThrownBy(() -> address(value, "100-0001", "東京都", "千代田区", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void 郵便番号が空なら拒否する(String value) {
            assertThatThrownBy(() -> address("JP", value, "東京都", "千代田区", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void 都道府県が空なら拒否する(String value) {
            assertThatThrownBy(() -> address("JP", "100-0001", value, "千代田区", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void 市区町村が空なら拒否する(String value) {
            assertThatThrownBy(() -> address("JP", "100-0001", "東京都", value, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 電話番号 {

        @Test
        void 未入力を許す() {
            assertThat(Phone.empty().value()).isNull();
            assertThatCode(() -> new Phone(null)).doesNotThrowAnyException();
        }

        @Test
        void 長さの境界を守る() {
            assertThatCode(() -> new Phone("0".repeat(50))).doesNotThrowAnyException();
            assertThatThrownBy(() -> new Phone("0".repeat(51)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 荷主コード {

        @Test
        void 採番から形式どおりに生成される() {
            assertThat(ShipperCode.of(1).value()).isEqualTo("SHP-000001");
            assertThat(ShipperCode.of(999999).value()).isEqualTo("SHP-999999");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"SHP-1", "SHP-1234567", "shp-000001", "000001", "SHP-00000A"})
        void 形式が違えば拒否する(String value) {
            assertThatThrownBy(() -> new ShipperCode(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 荷主名 {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void 空なら拒否する(String value) {
            assertThatThrownBy(() -> new ShipperName(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 長さの境界を守る() {
            assertThatCode(() -> new ShipperName("あ".repeat(200))).doesNotThrowAnyException();
            assertThatThrownBy(() -> new ShipperName("あ".repeat(201)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
