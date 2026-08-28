package com.example.bookingms.infrastructure.repositories;

/** shipper テーブルの 1 行。 */
public class ShipperRecord {

    private Long id;
    private String shipperCode;
    private String shipperType;
    private String name;
    private String email;
    private String address;
    private String phone;
    private String contractNumber;
    private java.math.BigDecimal discountRate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShipperCode() {
        return shipperCode;
    }

    public void setShipperCode(String shipperCode) {
        this.shipperCode = shipperCode;
    }

    public String getShipperType() {
        return shipperType;
    }

    public void setShipperType(String shipperType) {
        this.shipperType = shipperType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public java.math.BigDecimal getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(java.math.BigDecimal discountRate) {
        this.discountRate = discountRate;
    }
}
