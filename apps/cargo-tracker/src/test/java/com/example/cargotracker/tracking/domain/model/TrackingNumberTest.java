package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TrackingNumberTest {

    @Test
    @DisplayName("TRK-XXXXXXXX 形式の追跡番号を生成できる")
    void generateTrackingNumber() {
        TrackingNumber tn = TrackingNumber.generate();
        assertThat(tn.value()).matches("TRK-[A-Z0-9]{8}");
    }

    @Test
    @DisplayName("複数回生成した追跡番号はユニークである（確率的）")
    void generatedNumbersAreUnique() {
        TrackingNumber a = TrackingNumber.generate();
        TrackingNumber b = TrackingNumber.generate();
        // 同一になる確率は 1/36^8 ≈ 0 なので実質ユニーク
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("無効フォーマットの追跡番号は生成できない")
    void invalidFormatThrows() {
        assertThatThrownBy(() -> new TrackingNumber("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("有効な追跡番号を直接生成できる")
    void validTrackingNumber() {
        TrackingNumber tn = new TrackingNumber("TRK-ABC12345");
        assertThat(tn.value()).isEqualTo("TRK-ABC12345");
    }
}
