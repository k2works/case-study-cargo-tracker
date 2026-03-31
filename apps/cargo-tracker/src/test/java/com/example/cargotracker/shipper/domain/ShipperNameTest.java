package com.example.cargotracker.shipper.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ShipperName")
class ShipperNameTest {

    @Test
    @DisplayName("有効な氏名から ShipperName を生成できる")
    void createValidName() {
        ShipperName name = new ShipperName("山田 太郎");
        assertThat(name.value()).isEqualTo("山田 太郎");
    }

    @Test
    @DisplayName("空文字は受け入れない")
    void rejectEmpty() {
        assertThatThrownBy(() -> new ShipperName(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null は受け入れない")
    void rejectNull() {
        assertThatThrownBy(() -> new ShipperName(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("200 文字を超える名前は受け入れない")
    void rejectTooLong() {
        String tooLong = "a".repeat(201);
        assertThatThrownBy(() -> new ShipperName(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("200 文字の名前は許容される")
    void accept200Chars() {
        String maxLength = "a".repeat(200);
        ShipperName name = new ShipperName(maxLength);
        assertThat(name.value()).hasSize(200);
    }

    @Test
    @DisplayName("同じ値なら等価")
    void equality() {
        ShipperName a = new ShipperName("田中商事");
        ShipperName b = new ShipperName("田中商事");
        assertThat(a).isEqualTo(b);
    }
}
