package com.example.trackingms.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TrackingNumber} 値オブジェクトのテスト。
 *
 * <p>追跡番号は荷主に共有される識別子のため、推測困難な書式
 * （{@code TRK-} + 大文字英数 10 桁）を厳格に検証する。</p>
 */
class TrackingNumberTest {

    @Test
    void 正規書式の追跡番号を生成できる() {
        TrackingNumber tn = TrackingNumber.of("TRK-AB12CD3456");

        assertThat(tn.value()).isEqualTo("TRK-AB12CD3456");
    }

    @Test
    void 等価性は値で判定する() {
        TrackingNumber a = TrackingNumber.of("TRK-AB12CD3456");
        TrackingNumber b = TrackingNumber.of("TRK-AB12CD3456");

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void nullは拒否する() {
        assertThatThrownBy(() -> TrackingNumber.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("追跡番号");
    }

    @Test
    void 接頭辞TRK欠落は拒否する() {
        assertThatThrownBy(() -> TrackingNumber.of("AB12CD3456EF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRK-");
    }

    @Test
    void 大文字英数10桁未満は拒否する() {
        assertThatThrownBy(() -> TrackingNumber.of("TRK-ABC"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 小文字を含む追跡番号は拒否する() {
        assertThatThrownBy(() -> TrackingNumber.of("TRK-ab12CD3456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 記号を含む追跡番号は拒否する() {
        assertThatThrownBy(() -> TrackingNumber.of("TRK-AB12-CD345"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
