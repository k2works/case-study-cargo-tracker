package com.example.cargotracker.shipper.domain.model;

import com.example.cargotracker.shipper.domain.model.aggregates.CorporateShipper;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.aggregates.ShipperType;
import com.example.cargotracker.shipper.domain.model.valueobjects.ContractNumber;
import com.example.cargotracker.shipper.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shared.domain.model.ShipperId;
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
        ContractNumber contractNumber = new ContractNumber("CN-2026-0001");
        DiscountRate discountRate = new DiscountRate(new BigDecimal("0.1000"));

        CorporateShipper shipper = assertDoesNotThrow(() ->
                new CorporateShipper(corporateBaseShipper(), contractNumber, discountRate));

        assertEquals(contractNumber, shipper.getContractNumber());
        assertEquals(discountRate, shipper.getDiscountRate());
    }

    @Test
    void shouldThrowWhenDiscountRateIsNegative_boundary() {
        BigDecimal invalidRate = new BigDecimal("-0.0001");
        assertThrows(IllegalArgumentException.class, () -> new DiscountRate(invalidRate));
    }

    @Test
    void shouldAcceptDiscountRateAtLowerBound() {
        DiscountRate rate = assertDoesNotThrow(() -> new DiscountRate(new BigDecimal("0.0000")));
        assertEquals(BigDecimal.ZERO, rate.value());
    }

    @Test
    void shouldAcceptDiscountRateAtUpperBound() {
        DiscountRate rate = assertDoesNotThrow(() -> new DiscountRate(new BigDecimal("0.3000")));
        assertEquals(new BigDecimal("0.3"), rate.value());
    }

    @Test
    void shouldThrowWhenDiscountRateExceedsUpperBound_boundary() {
        BigDecimal invalidRate = new BigDecimal("0.3001");
        assertThrows(IllegalArgumentException.class, () -> new DiscountRate(invalidRate));
    }

    @Test
    void shouldThrowWhenDiscountRateIsNull() {
        ContractNumber contractNumber = new ContractNumber("CN-2026-0001");
        Shipper baseShipper = corporateBaseShipper();

        assertThrows(IllegalArgumentException.class, () ->
                new CorporateShipper(baseShipper, contractNumber, null));
    }

    private Shipper corporateBaseShipper() {
        return new Shipper(
                new ShipperId(UUID.randomUUID()),
                new ShipperCode("SHP-0002"),
                new ShipperName("テスト商事株式会社"),
                new Email("corp@example.com"),
                new Phone("03-9999-0000"),
                null,
                ShipperType.CORPORATE
        );
    }
}
