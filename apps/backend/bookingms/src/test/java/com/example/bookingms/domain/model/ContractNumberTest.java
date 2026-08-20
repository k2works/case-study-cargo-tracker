package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("契約番号")
class ContractNumberTest {

    @Test
    @DisplayName("値を保持する")
    void holdsValue() {
        assertThat(ContractNumber.of("CN-2026-0001").value()).isEqualTo("CN-2026-0001");
    }

    @Test
    @DisplayName("前後の空白は落とす（打ち込みの揺れで別の契約に見えないように）")
    void trims() {
        assertThat(ContractNumber.of("  CN-2026-0001  ").value()).isEqualTo("CN-2026-0001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("空の契約番号は受け付けない")
    void rejectsBlank(String value) {
        assertThatThrownBy(() -> ContractNumber.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("契約番号");
    }

    @Test
    @DisplayName("null は受け付けない")
    void rejectsNull() {
        assertThatThrownBy(() -> ContractNumber.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同じ番号は等しい")
    void equality() {
        assertThat(ContractNumber.of("CN-1")).isEqualTo(ContractNumber.of("CN-1"));
        assertThat(ContractNumber.of("CN-1")).hasSameHashCodeAs(ContractNumber.of("CN-1"));
        assertThat(ContractNumber.of("CN-1")).isNotEqualTo(ContractNumber.of("CN-2"));
        assertThat(ContractNumber.of("CN-1")).isNotEqualTo((Object) "CN-1");
    }

    @Test
    @DisplayName("表示は番号そのもの")
    void toStringIsValue() {
        assertThat(ContractNumber.of("CN-1")).hasToString("CN-1");
    }
}
