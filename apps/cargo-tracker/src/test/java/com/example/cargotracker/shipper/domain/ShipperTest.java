package com.example.cargotracker.shipper.domain;

import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.event.ShipperRegisteredEvent;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContactInfo;
import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Shipper 集約")
class ShipperTest {

    private Shipper createShipper(ShipperId shipperId, ShipperName shipperName, ContactInfo contactInfo) {
        return Shipper.registerIndividual(shipperId, shipperName, contactInfo);
    }

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
        Shipper shipper = createShipper(anyId(), anyName(), anyContact());

        assertThat(shipper.getId()).isNotNull();
        assertThat(shipper.getName().value()).isEqualTo("山田 太郎");
        assertThat(shipper.getContactInfo().email()).isEqualTo("yamada@example.com");
        assertThat(shipper.getCategory()).isEqualTo(CustomerCategory.INDIVIDUAL);
        assertThat(shipper.getCorporateContractInfo()).isNull();
    }

    @Test
    @DisplayName("登録時に ShipperRegisteredEvent が発行される")
    void registrationEmitsEvent() {
        Shipper shipper = createShipper(anyId(), anyName(), anyContact());

        assertThat(shipper.getDomainEvents()).hasSize(1);
        assertThat(shipper.getDomainEvents().get(0)).isInstanceOf(ShipperRegisteredEvent.class);

        ShipperRegisteredEvent event = (ShipperRegisteredEvent) shipper.getDomainEvents().get(0);
        assertThat(event.shipperId()).isEqualTo(shipper.getId());
    }

    @Test
    @DisplayName("ID が null の場合は登録できない")
    void rejectNullId() {
        ShipperName name = anyName();
        ContactInfo contact = anyContact();
        assertThatThrownBy(() -> createShipper(null, name, contact))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("名前が null の場合は登録できない")
    void rejectNullName() {
        ShipperId id = anyId();
        ContactInfo contact = anyContact();
        assertThatThrownBy(() -> createShipper(id, null, contact))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("連絡先が null の場合は登録できない")
    void rejectNullContactInfo() {
        ShipperId id = anyId();
        ShipperName name = anyName();
        assertThatThrownBy(() -> createShipper(id, name, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
