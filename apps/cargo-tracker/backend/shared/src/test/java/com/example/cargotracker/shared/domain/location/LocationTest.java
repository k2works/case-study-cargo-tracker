package com.example.cargotracker.shared.domain.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 共有カーネルの場所（domain-model.md「Shared Kernel」）。 */
class LocationTest {

    @Test
    @DisplayName("UN/LOCODE は英大文字 5 文字")
    void validatesUnLocodeFormat() {
        assertThat(new UnLocode("JPTYO").value()).isEqualTo("JPTYO");

        assertThatThrownBy(() -> new UnLocode("jptyo"))
                .as("小文字を通すと、同じ港が 2 通りの書き方で入る")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnLocode("JPTY")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnLocode("JPTYO1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnLocode(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("先頭 2 文字が国コードになる")
    void derivesCountryCode() {
        assertThat(new UnLocode("USNYC").countryCode()).isEqualTo(new CountryCode("US"));
    }

    @Test
    @DisplayName("同じ国かどうかを判定できる")
    void comparesCountry() {
        // 輸出免税の判定（Billing）がこれを使う。国コードを文字列で切り出す処理が
        // 各 BC に散ると、片方だけ直したときに判定が食い違う。
        Location tokyo = new Location(new UnLocode("JPTYO"), "東京");
        Location yokohama = new Location(new UnLocode("JPYOK"), "横浜");
        Location newYork = new Location(new UnLocode("USNYC"), "ニューヨーク");

        assertThat(tokyo.sameCountryAs(yokohama)).isTrue();
        assertThat(tokyo.sameCountryAs(newYork)).isFalse();
    }

    @Test
    @DisplayName("同一性は UN/LOCODE で決まる")
    void identityIsUnLocode() {
        // 港名は表示のための情報で、同じ港でも表記が揺れる。名前まで見て比較すると
        // 「東京」と「東京港」が別の港になる。
        assertThat(new Location(new UnLocode("JPTYO"), "東京"))
                .isEqualTo(new Location(new UnLocode("JPTYO"), "東京港"));
    }
}
