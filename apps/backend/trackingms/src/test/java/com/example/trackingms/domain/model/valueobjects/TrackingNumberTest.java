package com.example.trackingms.domain.model.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TrackingNumber 値オブジェクトテスト")
class TrackingNumberTest {

    @Test
    @DisplayName("正しい形式（TRK-XXXXXX）で生成できること")
    void shouldCreateValidTrackingNumber() {
        TrackingNumber tn = new TrackingNumber("TRK-123456");
        assertThat(tn.number()).isEqualTo("TRK-123456");
    }

    @Test
    @DisplayName("不正な形式は例外が発生すること")
    void shouldThrowExceptionForInvalidFormat() {
        assertThatThrownBy(() -> new TrackingNumber("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null は例外が発生すること")
    void shouldThrowExceptionForNull() {
        assertThatThrownBy(() -> new TrackingNumber(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("同じ番号は等価であること")
    void shouldBeEqualForSameNumber() {
        TrackingNumber a = new TrackingNumber("TRK-000001");
        TrackingNumber b = new TrackingNumber("TRK-000001");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("toString は追跡番号文字列を返すこと")
    void shouldReturnNumberAsString() {
        assertThat(new TrackingNumber("TRK-999999")).hasToString("TRK-999999");
    }
}
