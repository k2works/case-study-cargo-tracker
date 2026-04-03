package com.example.cargotracker.shipper.domain;

import com.example.cargotracker.shared.domain.model.ShipperId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ShipperId")
class ShipperIdTest {

    @Test
    @DisplayName("UUID から ShipperId を生成できる")
    void createFromUUID() {
        UUID uuid = UUID.randomUUID();
        ShipperId shipperId = new ShipperId(uuid);
        assertThat(shipperId.value()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("ファクトリメソッドで新しい ShipperId を生成できる")
    void generateNew() {
        ShipperId id1 = ShipperId.generate();
        ShipperId id2 = ShipperId.generate();
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("同じ UUID なら等価")
    void equality() {
        UUID uuid = UUID.randomUUID();
        ShipperId a = new ShipperId(uuid);
        ShipperId b = new ShipperId(uuid);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("null は受け入れない")
    void rejectNull() {
        assertThatThrownBy(() -> new ShipperId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
