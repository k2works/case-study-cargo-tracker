package com.example.cargotracker.shipper.domain.model;

import com.example.cargotracker.shipper.domain.model.aggregates.CorporateShipper;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorporateShipperTest {

    @Test
    void shouldCreateCorporateShipper() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        ShipperCode shipperCode = new ShipperCode("SHP-0002");
        ShipperName shipperName = new ShipperName("テスト商事株式会社");
        Email email = new Email("corp@example.com");
        Phone phone = new Phone("03-9999-0000");
        ContractNumber contractNumber = new ContractNumber("CN-2026-0001");
        DiscountRate discountRate = new DiscountRate(new BigDecimal("0.1000"));

        CorporateShipper shipper = assertDoesNotThrow(() ->
                new CorporateShipper(shipperId, shipperCode, shipperName, email, phone, contractNumber, discountRate));

        assertEquals(contractNumber, shipper.getContractNumber());
        assertEquals(discountRate, shipper.getDiscountRate());
    }

    @Test
    void shouldThrowWhenDiscountRateExceedsUpperBound() {
        BigDecimal tooLargeDiscountRate = new BigDecimal("0.1501");

        assertThrows(IllegalArgumentException.class, () -> new DiscountRate(tooLargeDiscountRate));
    }

    @Test
    void shouldThrowWhenDiscountRateIsNegative() {
        BigDecimal negativeDiscountRate = new BigDecimal("-0.0001");

        assertThrows(IllegalArgumentException.class, () -> new DiscountRate(negativeDiscountRate));
    }

    @Test
    void shouldThrowWhenDiscountRateIsNull() {
        ShipperId shipperId = new ShipperId(UUID.randomUUID());
        ShipperCode shipperCode = new ShipperCode("SHP-0002");
        ShipperName shipperName = new ShipperName("テスト商事株式会社");
        Email email = new Email("corp@example.com");
        ContractNumber contractNumber = new ContractNumber("CN-2026-0001");

        assertThrows(IllegalArgumentException.class, () ->
                new CorporateShipper(shipperId, shipperCode, shipperName, email, null, contractNumber, null));
    }
}
