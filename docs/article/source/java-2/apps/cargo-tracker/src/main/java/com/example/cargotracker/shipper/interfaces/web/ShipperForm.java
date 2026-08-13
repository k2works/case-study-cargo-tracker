package com.example.cargotracker.shipper.interfaces.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 荷主登録フォーム。
 *
 * <p>住所は番地以外が必須（US02 の受入基準）。ドメインの {@code Address} と
 * 同じ制約をここでも表明するのは、画面で早くフィードバックするためであり、
 * <strong>最後の砦はドメインと DB の制約</strong>である。
 */
public class ShipperForm {

    /**
     * 荷主種別。
     *
     * <p>IT6 までは {@code INDIVIDUAL} のみを許していた（法人は US03 の範囲であり、
     * <strong>押しても登録できない選択肢を置かない</strong>ため画面でも無効にしていた）。
     * US03 で法人を開いた。
     *
     * <p><strong>画面のラジオは案内であって制約ではない。</strong> 制約が無いと、
     * 細工した POST が「法人として送ったのに個人として保存される」という
     * 沈黙の取り違えになる。受け取れない値は受け取れないと言う。
     *
     * <p><strong>ここは受け取れる値の検査に留める。</strong> 種別と契約の整合
     * （法人には契約が要る・個人には付けられない）は {@code Shipper} が守る。
     */
    @Pattern(regexp = "INDIVIDUAL|CORPORATE", message = "荷主種別が不正です")
    @NotBlank(message = "荷主種別は必須です")
    private String shipperType = "INDIVIDUAL";

    @NotBlank(message = "荷主名は必須です")
    @Size(max = 200)
    private String name;

    /**
     * メールアドレス。
     *
     * <p><strong>ドメインの {@code Email} と同じ形式を要求する。</strong> Bean Validation の
     * {@code @Email} は {@code a@b} のようにドット無しのホストも通すため、そのままだと
     * 画面をすり抜けた値がドメインで例外になり、利用者には 500 として見える。
     * 画面の検証は案内であり、**案内と実際の受け入れ条件がずれていてはならない**。
     */
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

    /**
     * 契約番号（US03。法人のときだけ必須）。
     *
     * <p><strong>必須の判断はここでは行わない。</strong> 種別と契約の整合は
     * {@code Shipper} が守る。ここに条件を書き写すと、規則が 2 か所に散る。
     */
    private String contractNumber;

    /**
     * 契約割引率（百分率。{@code 10.00} 形式で受け取る）。
     *
     * <p><strong>上限をここに書かない。</strong> 0〜30% はドメインの不変条件であり
     * （{@code DiscountRate}）、画面に別の上限を書くと <strong>どちらが正なのか
     * 分からなくなる</strong>（旧版は画面が -50〜100% を許容していた）。
     */
    private java.math.BigDecimal discountRate;

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

    /** 法人として登録するか。 */
    public boolean isCorporate() {
        return "CORPORATE".equals(shipperType);
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
