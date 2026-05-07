package com.example.routingms.domain.model;

import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("VoyageNumber 値オブジェクトテスト")
class VoyageNumberTest {

    @Test
    @DisplayName("有効な航海番号で VoyageNumber を生成できること")
    void shouldCreateVoyageNumber() {
        VoyageNumber voyageNumber = new VoyageNumber("V001");
        assertThat(voyageNumber.getNumber()).isEqualTo("V001");
    }

    @Test
    @DisplayName("null の場合は例外をスローすること")
    void shouldThrowWhenNull() {
        assertThatThrownBy(() -> new VoyageNumber(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空文字の場合は例外をスローすること")
    void shouldThrowWhenBlank() {
        assertThatThrownBy(() -> new VoyageNumber("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同じ番号の VoyageNumber は等価であること")
    void shouldBeEqualWhenSameNumber() {
        VoyageNumber v1 = new VoyageNumber("V001");
        VoyageNumber v2 = new VoyageNumber("V001");
        assertThat(v1).isEqualTo(v2).hasSameHashCodeAs(v2);
    }

    @Test
    @DisplayName("toString が航海番号を含むこと")
    void shouldHaveToString() {
        VoyageNumber v = new VoyageNumber("V001");
        assertThat(v.toString()).contains("V001");
    }
}
