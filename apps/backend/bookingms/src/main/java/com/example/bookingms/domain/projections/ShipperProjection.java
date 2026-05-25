package com.example.bookingms.domain.projections;

import java.time.LocalDateTime;

/**
 * 荷主 Read Model (POJO + MyBatis ResultMap)。
 *
 * <p>{@code shipper} テーブルの各カラムに対応するフィールドを持つ。
 * MyBatis が setter を呼び出して値を設定するため、JPA アノテーションは付与しない。</p>
 */
public class ShipperProjection {

    private String shipperId;
    private String shipperType;
    private String name;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String countryCode;
    private String postalCode;
    private String email;
    private String phone;
    private String contractNumber;
    private java.math.BigDecimal discountRate;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public ShipperProjection() { /* MyBatis result mapping */ }

    public String getShipperId() { return shipperId; }
    public void setShipperId(String shipperId) { this.shipperId = shipperId; }

    public String getShipperType() { return shipperType; }
    public void setShipperType(String shipperType) { this.shipperType = shipperType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }

    public java.math.BigDecimal getDiscountRate() { return discountRate; }
    public void setDiscountRate(java.math.BigDecimal discountRate) { this.discountRate = discountRate; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
