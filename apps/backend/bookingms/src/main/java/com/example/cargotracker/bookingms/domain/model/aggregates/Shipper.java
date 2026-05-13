package com.example.cargotracker.bookingms.domain.model.aggregates;

import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperType;

import java.math.BigDecimal;
import java.util.UUID;

public class Shipper {

    private Long id;
    private final String shipperCode;
    private final ShipperType shipperType;
    private final String name;
    private final String email;
    private final String phone;
    private final String contractNumber;
    private final BigDecimal discountRate;

    public record PersistedState(Long id, String shipperCode, ShipperType shipperType,
                                 String name, String email, String phone,
                                 String contractNumber, BigDecimal discountRate) {}

    private Shipper(String shipperCode, ShipperType shipperType, String name, String email,
                    String phone, String contractNumber, BigDecimal discountRate) {
        this.shipperCode = shipperCode;
        this.shipperType = shipperType;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.contractNumber = contractNumber;
        this.discountRate = discountRate;
    }

    public Shipper(PersistedState state) {
        this.id = state.id();
        this.shipperCode = state.shipperCode();
        this.shipperType = state.shipperType();
        this.name = state.name();
        this.email = state.email();
        this.phone = state.phone();
        this.contractNumber = state.contractNumber();
        this.discountRate = state.discountRate();
    }

    public static Shipper createIndividual(String name, String email, String phone) {
        validateName(name);
        return new Shipper(generateCode(), ShipperType.INDIVIDUAL, name, email, phone, null, null);
    }

    public static Shipper createCorporate(String name, String email, String phone,
                                          String contractNumber, BigDecimal discountRate) {
        validateName(name);
        validateDiscountRate(discountRate);
        return new Shipper(generateCode(), ShipperType.CORPORATE, name, email, phone,
                contractNumber, discountRate);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name は必須です");
        }
    }

    private static void validateDiscountRate(BigDecimal discountRate) {
        if (discountRate != null && (discountRate.compareTo(BigDecimal.ZERO) < 0 ||
                discountRate.compareTo(new BigDecimal("0.30")) > 0)) {
            throw new IllegalArgumentException("割引率は 0 から 0.30 の範囲で指定してください");
        }
    }

    private static String generateCode() {
        return "SHP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public Long getId() {
        return id;
    }

    public String getShipperCode() {
        return shipperCode;
    }

    public ShipperType getShipperType() {
        return shipperType;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public BigDecimal getDiscountRate() {
        return discountRate;
    }
}
