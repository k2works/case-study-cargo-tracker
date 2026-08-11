package com.example.cargotracker.estimation.interfaces.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 見積の入力（US01 の受入基準 1・6）。
 *
 * <p><strong>荷主は任意である。</strong> 見積は予約前の照会であり、荷主が確定して
 * いない段階でも作れる（`ui_design.md`）。
 */
public class EstimateForm {

    @NotBlank(message = "出発地は必須です")
    private String origin;

    @NotBlank(message = "目的地は必須です")
    private String destination;

    @NotNull(message = "希望到着期限は必須です")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate arrivalDeadline;

    @NotBlank(message = "貨物種別は必須です")
    private String cargoType;

    @NotNull(message = "重量は必須です")
    @Positive(message = "重量は正の値です")
    private BigDecimal weightKg;

    /** 危険物クラス（危険物のときだけ）。 */
    private String hazardClass;

    /** UN 番号（危険物のときだけ）。 */
    private String unNumber;

    /** 正式輸送品名（危険物のときだけ）。 */
    private String properShippingName;

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

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

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

    /**
     * 危険物か。
     *
     * <p><strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> テンプレートで
     * 文字列を比べると、種別を足したときに片方だけ古くなる。
     */
    public boolean isHazardous() {
        return "HAZARDOUS".equals(cargoType);
    }
}
