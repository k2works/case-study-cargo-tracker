package com.example.cargotracker.shipper.infrastructure.repositories;

import java.math.BigDecimal;

/**
 * 読み取りクエリの生の行。表示用への変換は {@link MyBatisShipperQueryService} が行う。
 *
 * <p><strong>平坦なのは SQL の結果がそうだからである。</strong> MyBatis は 1 行の
 * 結果集合を入れ子のレコードへ直接は組み立てられない。表示用の
 * {@code ShipperView} は意味のまとまりごとに分けてあり（IT17 の R6）、
 * <strong>その組み立てをここではなくクエリサービスが行う</strong>。
 * Booking も同じ形である（{@code BookingQueryRow}）。
 */
public class ShipperQueryRow {

    private String id;
    private String shipperCode;
    private String shipperType;
    private String typeLabel;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String addressCountry;
    private String addressPostalCode;
    private String addressRegion;
    private String addressCity;
    private String addressStreet;
    private String contractNumber;
    private BigDecimal discountRatePercentage;
    private long version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddressCountry() {
        return addressCountry;
    }

    public void setAddressCountry(String addressCountry) {
        this.addressCountry = addressCountry;
    }

    public String getAddressPostalCode() {
        return addressPostalCode;
    }

    public void setAddressPostalCode(String addressPostalCode) {
        this.addressPostalCode = addressPostalCode;
    }

    public String getAddressRegion() {
        return addressRegion;
    }

    public void setAddressRegion(String addressRegion) {
        this.addressRegion = addressRegion;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public void setAddressCity(String addressCity) {
        this.addressCity = addressCity;
    }

    public String getAddressStreet() {
        return addressStreet;
    }

    public void setAddressStreet(String addressStreet) {
        this.addressStreet = addressStreet;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public BigDecimal getDiscountRatePercentage() {
        return discountRatePercentage;
    }

    public void setDiscountRatePercentage(BigDecimal discountRatePercentage) {
        this.discountRatePercentage = discountRatePercentage;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
