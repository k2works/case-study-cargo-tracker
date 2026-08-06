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
        })
        void 不正な形式を拒否する(String value) {
            assertThatThrownBy(() -> new Email(value))
                    .isInstanceOf(IllegalArgumentException.class);
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
