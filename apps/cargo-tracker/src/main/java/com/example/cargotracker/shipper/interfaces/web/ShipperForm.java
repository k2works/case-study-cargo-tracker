package com.example.cargotracker.shipper.interfaces.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 荷主登録フォーム。
 *
 * <p>住所は番地以外が必須（US02 の受入基準）。ドメインの {@code Address} と
 * 同じ制約をここでも表明するのは、画面で早くフィードバックするためであり、
 * <strong>最後の砦はドメインと DB の制約</strong>である。
 */
public class ShipperForm {

    @NotBlank(message = "荷主種別は必須です")
    private String shipperType = "INDIVIDUAL";

    @NotBlank(message = "荷主名は必須です")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "メールアドレスは必須です")
    @Email(message = "メールアドレスの形式が不正です")
    @Size(max = 200)
    private String email;

    @Size(max = 50)
    private String phone;

    @NotBlank(message = "国は必須です")
    @Size(min = 2, max = 2, message = "国コードは 2 文字です")
    private String addressCountry = "JP";

    @NotBlank(message = "郵便番号は必須です")
    @Size(max = 20)
    private String addressPostalCode;

    @NotBlank(message = "都道府県は必須です")
    @Size(max = 100)
    private String addressRegion;

    @NotBlank(message = "市区町村は必須です")
    @Size(max = 100)
    private String addressCity;

    @Size(max = 200)
    private String addressStreet;

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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
}
