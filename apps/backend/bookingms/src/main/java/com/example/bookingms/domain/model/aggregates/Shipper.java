package com.example.bookingms.domain.model.aggregates;

import com.example.bookingms.domain.model.valueobjects.ShipperType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 荷主（Shipper）集約ルート
 */
public class Shipper {

    private Long id;
    private final String shipperCode;
    private final ShipperType shipperType;
    private final String name;
    private final String email;
    private final String phone;
    private final String contractNumber;
    private final BigDecimal discountRate;

    /**
     * 個人荷主作成ファクトリメソッド
     */
    public static Shipper createIndividual(String name, String email, String phone) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(email, "email must not be null");
        return new Shipper(new PersistedState(null, null, ShipperType.INDIVIDUAL, name, email, phone, null, null));
    }

    /**
     * 法人荷主作成ファクトリメソッド
     */
    public static Shipper createCorporate(String name, String email, String phone,
                                           String contractNumber, BigDecimal discountRate) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(contractNumber, "contractNumber must not be null");
        Objects.requireNonNull(discountRate, "discountRate must not be null");
        if (discountRate.compareTo(BigDecimal.ZERO) < 0 || discountRate.compareTo(new BigDecimal("0.30")) > 0) {
            throw new IllegalArgumentException("discountRate must be between 0 and 0.30");
        }
        return new Shipper(new PersistedState(null, null, ShipperType.CORPORATE, name, email, phone, contractNumber, discountRate));
    }

    /**
     * DB からの復元に使用する状態 record
     */
    public record PersistedState(Long id, String shipperCode, ShipperType shipperType,
                                  String name, String email, String phone,
                                  String contractNumber, BigDecimal discountRate) {}

    /**
     * DBからの復元コンストラクタ
     */
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShipperCode() { return shipperCode; }
    public ShipperType getShipperType() { return shipperType; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getContractNumber() { return contractNumber; }
    public BigDecimal getDiscountRate() { return discountRate; }
}
