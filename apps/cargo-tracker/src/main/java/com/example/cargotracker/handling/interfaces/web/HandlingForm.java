package com.example.cargotracker.handling.interfaces.web;

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
            message = "追跡番号の形式が正しくありません。TRK-20260901-0001 のような形式で入力してください")
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

    /**
     * 追跡番号を受け取る。<strong>スキャナが送る形をそのまま受け入れる</strong>
     * （IT6 レビュー H14）。
     *
     * <p>バーコードスキャナはキーボードとして打ち込み、多くの機種は末尾に改行を送る。
     * 機種や設定によっては小文字で送り、手入力では前後に空白が混じる。
     * <strong>そのどれもを書式の検査が弾く。</strong> 弾かれた作業員には直しようがない
     * — 画面に見えている文字列は正しいからである。
     *
     * <p><strong>整えるのは入れ物の形だけである。</strong> 区切りや桁数を補って
     * 正しい形に作り変えることはしない。それをすると、別の貨物の番号を
     * 受け付けてしまう。
     */
    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = normalizeScannedInput(trackingNumber);
    }

    /**
     * スキャナ・手入力の揺れを整える。
     *
     * <p>{@link String#strip()} は半角空白だけでなく<strong>全角空白・改行・タブも
     * 取り除く</strong>（{@link Character#isWhitespace} が真となる文字すべて）。
     * {@code trim()} では全角空白が残るため使わない。
     */
    private static String normalizeScannedInput(String value) {
        // 未入力はそのまま残す。空文字を null に変えると必須の検査のことばが変わる
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.strip().toUpperCase(java.util.Locale.ROOT);
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
