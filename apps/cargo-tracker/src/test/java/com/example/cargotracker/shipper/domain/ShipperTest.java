package com.example.cargotracker.shipper.domain;

import com.example.cargotracker.shipper.domain.event.ShipperRegisteredEvent;
import com.example.cargotracker.shipper.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Shipper 集約")
class ShipperTest {

    private ShipperId anyId() {
        return ShipperId.generate();
    }

    private ShipperName anyName() {
        return new ShipperName("山田 太郎");
    }

    private ContactInfo anyContact() {
        return new ContactInfo("yamada@example.com", "090-0000-0000");
    }

    @Test
    @DisplayName("個人荷主を登録できる")
    void registerIndividualShipper() {
        Shipper shipper = Shipper.registerIndividual(anyId(), anyName(), anyContact());

        assertThat(shipper.getId()).isNotNull();
        assertThat(shipper.getName().value()).isEqualTo("山田 太郎");
        assertThat(shipper.getContactInfo().email()).isEqualTo("yamada@example.com");
        assertThat(shipper.getCategory()).isEqualTo(CustomerCategory.INDIVIDUAL);
        assertThat(shipper.getCorporateContractInfo()).isNull();
    }

    @Test
    @DisplayName("登録時に ShipperRegisteredEvent が発行される")
    void registrationEmitsEvent() {
        Shipper shipper = Shipper.registerIndividual(anyId(), anyName(), anyContact());

        assertThat(shipper.getDomainEvents()).hasSize(1);
        assertThat(shipper.getDomainEvents().get(0)).isInstanceOf(ShipperRegisteredEvent.class);

        ShipperRegisteredEvent event = (ShipperRegisteredEvent) shipper.getDomainEvents().get(0);
        assertThat(event.shipperId()).isEqualTo(shipper.getId());
    }

    @Test
    @DisplayName("ID が null の場合は登録できない")
    void rejectNullId() {
        assertThatThrownBy(() -> Shipper.registerIndividual(null, anyName(), anyContact()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("名前が null の場合は登録できない")
    void rejectNullName() {
        assertThatThrownBy(() -> Shipper.registerIndividual(anyId(), null, anyContact()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("連絡先が null の場合は登録できない")
    void rejectNullContactInfo() {
        assertThatThrownBy(() -> Shipper.registerIndividual(anyId(), anyName(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
