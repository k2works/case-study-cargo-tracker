package com.example.cargotracker.shipper.interfaces.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 荷主訂正フォーム（US32）。
 *
 * <p><strong>荷主コードと荷主種別のフィールドを持たない。</strong> US32 の受入基準が
 * 「変更できない」と定めているためである。画面から欄を消すだけだと、リクエストを
 * 直接組み立てれば変更できてしまう。**受け取らないことが「変更できない」の実装である。**
 *
 * <p>{@code version} は楽観的ロック用。画面が読み取った時点のバージョンを持ち回り、
 * 保存時に DB の値と突き合わせる。
 */
public class ShipperEditForm {

    private long version;

    @NotBlank(message = "荷主名は必須です")
    @Size(max = 200)
    private String name;

    /** メールアドレス。形式はドメインの {@code Email} と揃える（{@code ShipperForm} と同じ理由）。 */
    @NotBlank(message = "メールアドレスは必須です")
    @Pattern(regexp = "^[^@\\s]+@[^@\\s.]+(?:\\.[^@\\s.]+){1,10}$",
            message = "メールアドレスの形式が不正です")
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

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
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
