package com.example.cargotracker.booking.interfaces.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 見積から引き継ぐ輸送条件（US01。`ui_design.md` の画面遷移図）。
 *
 * <p><strong>同じ条件を 2 度入力させない。</strong> 見積で入れた出発地・目的地・
 * 期限・貨物種別・重量を、予約登録のフォームに埋める。
 *
 * <p><strong>渡ってこなくてもよい。</strong> 荷主詳細からの遷移や直接の登録では
 * 何も入らず、フォームは空のまま開く。
 */
public class BookingPrefill {

    private String origin;
    private String destination;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate arrivalDeadline;

    private String cargoType;
    private BigDecimal weight;

    /**
     * 受け取った値だけをフォームへ写す。
     *
     * <p><strong>空の値で上書きしない。</strong> 一部だけ渡ってきたときに、
     * 他の欄まで消してしまう。
     */
    public void applyTo(BookingForm form) {
        if (origin != null && !origin.isBlank()) {
            form.setOrigin(origin);
        }
        if (destination != null && !destination.isBlank()) {
            form.setDestination(destination);
        }
        if (arrivalDeadline != null) {
            form.setArrivalDeadline(arrivalDeadline);
        }
        if (cargoType != null && !cargoType.isBlank()) {
            form.setCargoType(cargoType);
        }
        if (weight != null) {
            form.setWeight(weight);
        }
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
}
