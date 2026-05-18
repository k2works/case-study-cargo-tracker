package com.example.cargotracker.trackingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UnLocode 値オブジェクト")
class UnLocodeTest {

    @Test
    @DisplayName("5 文字英数（先頭 2 文字は国コード）で生成できる")
    void canCreate() {
        assertThat(new UnLocode("JPTYO").value()).isEqualTo("JPTYO");
        assertThat(new UnLocode("SGSIN").value()).isEqualTo("SGSIN");
    }

    @Test
    @DisplayName("国コード以外に数字を含められる")
    void allowNumericLocode() {
        assertThat(new UnLocode("US123").value()).isEqualTo("US123");
    }

    @Test
    @DisplayName("4 文字以下は拒否する")
    void rejectShort() {
        assertThatThrownBy(() -> new UnLocode("JPTO"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("先頭 2 文字が国コード（A-Z）でない場合は拒否する")
    void rejectInvalidCountry() {
        assertThatThrownBy(() -> new UnLocode("12TYO"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
