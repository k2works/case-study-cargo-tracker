package com.example.cargotracker.booking.interfaces.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 貨物予約登録フォーム（US04）。
 *
 * <p>画面の検証は案内であり、<strong>最後の砦はドメインと DB の制約</strong>である。
 * ただし案内と実際の受け入れ条件がずれていてはならないため、形式はドメインの
 * 値オブジェクトと揃える（UN/LOCODE 5 文字、重量は正の値、個数は 1 以上）。
 *
 * <p>出発地と目的地が同じ場合・到着期限が過去の場合の拒否は、
 * 項目単体では判定できないためドメインが行う。
 */
public class BookingForm {

    /** 荷主コード。荷主 ID ではなく業務コードで指定する（暗記を前提にしない）。 */
    @NotBlank(message = "荷主コードは必須です")
    @Pattern(regexp = "SHP-\\d{6}", message = "荷主コードは SHP-999999 形式です")
    private String shipperCode;

    @NotBlank(message = "出発地は必須です")
    @Pattern(regexp = "[A-Z]{2}[A-Z0-9]{3}", message = "出発地は UN/LOCODE（英大文字 5 文字）です")
    private String origin;

    @NotBlank(message = "目的地は必須です")
    @Pattern(regexp = "[A-Z]{2}[A-Z0-9]{3}", message = "目的地は UN/LOCODE（英大文字 5 文字）です")
    private String destination;

    @NotNull(message = "希望到着期限は必須です")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate arrivalDeadline;

    @NotBlank(message = "貨物種別は必須です")
    @Pattern(regexp = "GENERAL|HAZARDOUS|REFRIGERATED", message = "貨物種別が不正です")
    private String cargoType = "GENERAL";

    @NotNull(message = "重量は必須です")
    @jakarta.validation.constraints.DecimalMin(
            value = "0.001", message = "重量は 0 より大きい値です")
    @jakarta.validation.constraints.Digits(
            integer = 7, fraction = 3, message = "重量は小数第 3 位までです")
    private BigDecimal weight;

    @Min(value = 1, message = "個数は 1 以上です")
    private Integer quantity;

    private BigDecimal dimensionLength;
    private BigDecimal dimensionWidth;
    private BigDecimal dimensionHeight;

    @Size(max = 500, message = "品名は 500 文字までです")
    private String description;

    /**
     * 危険物クラス（US05。<strong>危険物のときだけ必須</strong>）。
     *
     * <p><strong>必須の判断はここでは行わない。</strong> 種別と申告の整合は
     * {@code CargoSpecification} が守る。ここに条件を書き写すと規則が 2 か所に散る
     * （US03 の契約情報と同じ扱い）。
     */
    private String hazardClass;

    /** UN 番号（US05）。 */
    private String unNumber;

    /** 正式輸送品名（US05）。 */
    private String properShippingName;

    /** 最低温度（US05。<strong>冷凍・冷蔵のときだけ必須</strong>）。 */
    private java.math.BigDecimal minTemperature;

    /** 最高温度（US05）。 */
    private java.math.BigDecimal maxTemperature;

    /** 温度の単位（{@code CELSIUS} / {@code FAHRENHEIT}）。 */
    private String temperatureUnit;

    public String getHazardClass() {
        return hazardClass;
    }

    public void setHazardClass(String hazardClass) {
        this.hazardClass = hazardClass;
    }

    public String getUnNumber() {
        return unNumber;
    }

    public void setUnNumber(String unNumber) {
        this.unNumber = unNumber;
    }

    public String getProperShippingName() {
        return properShippingName;
    }

    public void setProperShippingName(String properShippingName) {
        this.properShippingName = properShippingName;
    }

    public java.math.BigDecimal getMinTemperature() {
        return minTemperature;
    }

    public void setMinTemperature(java.math.BigDecimal minTemperature) {
        this.minTemperature = minTemperature;
    }

    public java.math.BigDecimal getMaxTemperature() {
        return maxTemperature;
    }

    public void setMaxTemperature(java.math.BigDecimal maxTemperature) {
        this.maxTemperature = maxTemperature;
    }

    public String getTemperatureUnit() {
        return temperatureUnit;
    }

    public void setTemperatureUnit(String temperatureUnit) {
        this.temperatureUnit = temperatureUnit;
    }

    public String getShipperCode() {
        return shipperCode;
    }

    public void setShipperCode(String shipperCode) {
        this.shipperCode = shipperCode;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    public void setArrivalDeadline(LocalDate arrivalDeadline) {
        this.arrivalDeadline = arrivalDeadline;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(String cargoType) {
        this.cargoType = cargoType;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getDimensionLength() {
        return dimensionLength;
    }

    public void setDimensionLength(BigDecimal dimensionLength) {
        this.dimensionLength = dimensionLength;
    }

    public BigDecimal getDimensionWidth() {
        return dimensionWidth;
    }

    public void setDimensionWidth(BigDecimal dimensionWidth) {
        this.dimensionWidth = dimensionWidth;
    }

    public BigDecimal getDimensionHeight() {
        return dimensionHeight;
    }

    public void setDimensionHeight(BigDecimal dimensionHeight) {
        this.dimensionHeight = dimensionHeight;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
