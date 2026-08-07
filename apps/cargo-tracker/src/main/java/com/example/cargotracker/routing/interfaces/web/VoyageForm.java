package com.example.cargotracker.routing.interfaces.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 航海スケジュール登録フォーム（US24）。
 *
 * <p>寄港地は<strong>順序付きの区間の並び</strong>として入力する。
 * 「出発港・到着港・寄港地」を別々に受け取る形にしないのは、
 * **区間ごとの発着時刻が必要**であり、寄港地の一覧だけでは航海を組み立てられないためである。
 *
 * <p>連結制約（区間 n の到着港 = 区間 n+1 の出発港）と時系列の検証は
 * ドメイン（{@code Schedule}）が行う。画面はここでは検証しない。
 * **同じ規則を 2 か所に書くと必ず片方だけが更新される。**
 */
public class VoyageForm {

    @NotBlank(message = "航海番号は必須です")
    @Pattern(regexp = "[A-Za-z0-9-]{1,20}",
            message = "航海番号は英数字とハイフンで 20 文字までです")
    private String voyageNumber;

    @NotBlank(message = "船名は必須です")
    @Size(max = 100)
    private String vesselName;

    @NotBlank(message = "運送会社は必須です")
    @Size(max = 100)
    private String carrierName;

    @NotEmpty(message = "取り扱える貨物種別を 1 つ以上選んでください")
    private List<String> cargoTypes = new ArrayList<>();

    /** 積載可能重量。**容量が分からない便を作らない**ため必須である（US09 の空き容量判定）。 */
    @NotNull(message = "積載可能重量は必須です")
    @DecimalMin(value = "0.001", message = "積載可能重量は 0 より大きい値です")
    private BigDecimal capacityWeightKg;

    @NotEmpty(message = "運送区間を 1 つ以上入力してください")
    private List<MovementForm> movements = new ArrayList<>(List.of(new MovementForm()));

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getVesselName() {
        return vesselName;
    }

    public void setVesselName(String vesselName) {
        this.vesselName = vesselName;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public List<String> getCargoTypes() {
        return cargoTypes;
    }

    public void setCargoTypes(List<String> cargoTypes) {
        this.cargoTypes = cargoTypes;
    }

    public BigDecimal getCapacityWeightKg() {
        return capacityWeightKg;
    }

    public void setCapacityWeightKg(BigDecimal capacityWeightKg) {
        this.capacityWeightKg = capacityWeightKg;
    }

    public List<MovementForm> getMovements() {
        return movements;
    }

    public void setMovements(List<MovementForm> movements) {
        this.movements = movements;
    }

    /** 運送区間 1 つ分の入力。 */
    public static class MovementForm {

        @NotBlank(message = "出発港は必須です")
        @Pattern(regexp = "[A-Z]{2}[A-Z0-9]{3}",
                message = "出発港は UN/LOCODE（英大文字 5 文字）です")
        private String departure;

        @NotBlank(message = "到着港は必須です")
        @Pattern(regexp = "[A-Z]{2}[A-Z0-9]{3}",
                message = "到着港は UN/LOCODE（英大文字 5 文字）です")
        private String arrival;

        @jakarta.validation.constraints.NotNull(message = "出発日時は必須です")
        @org.springframework.format.annotation.DateTimeFormat(
                iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
        private java.time.LocalDateTime departureTime;

        @jakarta.validation.constraints.NotNull(message = "到着日時は必須です")
        @org.springframework.format.annotation.DateTimeFormat(
                iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
        private java.time.LocalDateTime arrivalTime;

        public String getDeparture() {
            return departure;
        }

        public void setDeparture(String departure) {
            this.departure = departure;
        }

        public String getArrival() {
            return arrival;
        }

        public void setArrival(String arrival) {
            this.arrival = arrival;
        }

        public java.time.LocalDateTime getDepartureTime() {
            return departureTime;
        }

        public void setDepartureTime(java.time.LocalDateTime departureTime) {
            this.departureTime = departureTime;
        }

        public java.time.LocalDateTime getArrivalTime() {
            return arrivalTime;
        }

        public void setArrivalTime(java.time.LocalDateTime arrivalTime) {
            this.arrivalTime = arrivalTime;
        }
    }
}
