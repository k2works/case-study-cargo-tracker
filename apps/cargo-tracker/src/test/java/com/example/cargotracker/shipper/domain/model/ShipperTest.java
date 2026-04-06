package com.example.cargotracker.shipper.domain.model;

import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.aggregates.ShipperType;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipperTest {

    @Test
    void shouldCreateShipperWithValidData() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        ShipperCode shipperCode = new ShipperCode("SHP-0001");
        ShipperName shipperName = new ShipperName("テスト荷主");
        Email email = new Email("shipper@example.com");
        Phone phone = new Phone("03-1234-5678");

        Shipper shipper = assertDoesNotThrow(() ->
                new Shipper(shipperId, shipperCode, shipperName, email, phone, null, ShipperType.INDIVIDUAL));

        assertEquals(shipperId, shipper.getId());
        assertEquals(shipperCode, shipper.getCode());
        assertEquals(shipperName, shipper.getName());
        assertEquals(email, shipper.getEmail());
        assertEquals(phone, shipper.getPhone());
        assertEquals(ShipperType.INDIVIDUAL, shipper.getShipperType());
    }

    @Test
    void shouldUseUuidFormatForShipperId() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());

        assertDoesNotThrow(() -> UUID.fromString(shipperId.toString()));
    }

    @Test
    void shouldThrowWhenNameIsNull() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        ShipperCode shipperCode = new ShipperCode("SHP-0001");
        Email email = new Email("shipper@example.com");

        assertThrows(IllegalArgumentException.class, () ->
                new Shipper(shipperId, shipperCode, null, email, null, null, ShipperType.INDIVIDUAL));
    }

    @Test
    void shouldThrowWhenEmailFormatIsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new Email("invalid-email"));
    }

    @Test
    void shouldThrowWhenIdIsNull() {
        ShipperCode code = new ShipperCode("SHP-0001");
        ShipperName name = new ShipperName("テスト荷主");
        Email email = new Email("shipper@example.com");

        assertThrows(IllegalArgumentException.class, () ->
                new Shipper(null, code, name, email, null, null, ShipperType.INDIVIDUAL));
    }

    @Test
    void shouldThrowWhenCodeIsNull() {
        ShipperId id = new ShipperId(UUID.randomUUID());
        ShipperName name = new ShipperName("テスト荷主");
        Email email = new Email("shipper@example.com");

        assertThrows(IllegalArgumentException.class, () ->
                new Shipper(id, null, name, email, null, null, ShipperType.INDIVIDUAL));
    }

    @Test
    void shouldThrowWhenEmailIsNull() {
        ShipperId id = new ShipperId(UUID.randomUUID());
        ShipperCode code = new ShipperCode("SHP-0001");
        ShipperName name = new ShipperName("テスト荷主");

        assertThrows(IllegalArgumentException.class, () ->
                new Shipper(id, code, name, null, null, null, ShipperType.INDIVIDUAL));
    }

    @Test
    void shouldThrowWhenShipperTypeIsNull() {
        ShipperId id = new ShipperId(UUID.randomUUID());
        ShipperCode code = new ShipperCode("SHP-0001");
        ShipperName name = new ShipperName("テスト荷主");
        Email email = new Email("shipper@example.com");

        assertThrows(IllegalArgumentException.class, () ->
                new Shipper(id, code, name, email, null, null, null));
    }
}
