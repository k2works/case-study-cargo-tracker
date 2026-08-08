package com.example.cargotracker.tracking.handling.interfaces.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 荷役作業登録のフォーム（US15）。
 *
 * <p><strong>入力の検査はここ、業務の判断は集約。</strong> ここで見るのは
 * 「書式として受け取れるか」だけであり、予定ルートとの照合は
 * {@code HandlingActivity.isValidFor} が行う。
 */
public class HandlingForm {

    @NotBlank(message = "追跡番号を入力してください")
    @Pattern(regexp = "^TRK-\\d{8}-\\d{4}$",
            message = "追跡番号の形式が正しくありません（TRK-YYYYMMDD-NNNN）")
    private String trackingNumber;

    @NotBlank(message = "荷役種別を選択してください")
    private String type;

    @NotNull(message = "作業日時を入力してください")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime completionTime;

    @NotBlank(message = "作業場所を入力してください")
    @Pattern(regexp = "^[A-Z]{2}[A-Z0-9]{3}$",
            message = "作業場所は UN/LOCODE 形式（5 文字）で入力してください")
    private String locationUnlocode;

    /** 航海番号。積込・荷降しでは必須だが、**その判断は集約が持つ**。 */
    private String voyageNumber;

    private String operatorName;

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDateTime getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(LocalDateTime completionTime) {
        this.completionTime = completionTime;
    }

    public String getLocationUnlocode() {
        return locationUnlocode;
    }

    public void setLocationUnlocode(String locationUnlocode) {
        this.locationUnlocode = locationUnlocode;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }
}
