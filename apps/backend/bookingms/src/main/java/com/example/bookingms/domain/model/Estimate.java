package com.example.bookingms.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 輸送見積（US01）。
 *
 * <p><strong>営業担当者が荷主に「いくらで何日か」を答えるための道具である。</strong>
 * 予約ではない——見積を作っても貨物は動かない。
 *
 * <p><strong>概算料金は US21 と同じ式で出す</strong>（[ADR-028] 決定 6）。式を 2 つ
 * 持つと必ずずれ、営業は毎回「見積はあくまで概算です」と言うことになる。
 *
 * <p><strong>間に合う候補が無いことと、候補が 1 本も無いことは別である</strong>
 * （受入基準 01-5）。前者は「最短でも N 日超過します」と言える——荷主に折り返す
 * 言葉があるかどうかが違う。
 */
public final class Estimate {

    private final EstimateId estimateId;
    private final EstimateNumber estimateNumber;
    private final String originUnLocode;
    private final String destinationUnLocode;
    private final LocalDate arrivalDeadline;
    private final CargoType cargoType;
    private final BigDecimal weightKg;
    private final List<RouteCandidate> candidates;
    private final EstimateStatus status;

    private Estimate(EstimateId estimateId, EstimateNumber estimateNumber, String originUnLocode,
            String destinationUnLocode, LocalDate arrivalDeadline, CargoType cargoType,
            BigDecimal weightKg, List<RouteCandidate> candidates, EstimateStatus status) {
        this.estimateId = estimateId;
        this.estimateNumber = estimateNumber;
        this.originUnLocode = originUnLocode;
        this.destinationUnLocode = destinationUnLocode;
        this.arrivalDeadline = arrivalDeadline;
        this.cargoType = cargoType;
        this.weightKg = weightKg;
        // **写して持つ。** 呼び出し元が渡したあとの書き換えでこちらの中身が変わらないように
        this.candidates = List.copyOf(candidates);
        this.status = status;
    }

    /**
     * 見積を作る（受入基準 01-1・01-4）。
     *
     * <p>入力は 5 項目——出発地・目的地・希望期限・貨物種別・重量。
     */
    public static Estimate create(EstimateId estimateId, EstimateNumber estimateNumber,
            String originUnLocode, String destinationUnLocode, LocalDate arrivalDeadline,
            CargoType cargoType, BigDecimal weightKg, List<RouteCandidate> candidates) {
        if (estimateId == null || estimateNumber == null) {
            throw new IllegalArgumentException("見積の識別子と見積番号を指定してください");
        }
        if (originUnLocode == null || originUnLocode.isBlank()
                || destinationUnLocode == null || destinationUnLocode.isBlank()) {
            throw new IllegalArgumentException("出発地と目的地を指定してください");
        }
        if (originUnLocode.equals(destinationUnLocode)) {
            // 同じ港へは運べない。予約（`RouteSpecification`）と同じ規則である
            throw new IllegalArgumentException("出発地と目的地が同じです: " + originUnLocode);
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("希望期限を指定してください");
        }
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別を指定してください");
        }
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("重量は 0 より大きい値で指定してください: " + weightKg);
        }
        return new Estimate(estimateId, estimateNumber, originUnLocode, destinationUnLocode,
                arrivalDeadline, cargoType, weightKg,
                candidates == null ? List.of() : candidates, EstimateStatus.CREATED);
    }

    /**
     * 永続化された行から復元する。
     *
     * <p><strong>ここでは検査しない</strong>（新しい不変条件は既存行を壊す）。
     */
    public static Estimate restore(EstimateId estimateId, EstimateNumber estimateNumber,
            String originUnLocode, String destinationUnLocode, LocalDate arrivalDeadline,
            CargoType cargoType, BigDecimal weightKg, List<RouteCandidate> candidates,
            EstimateStatus status) {
        return new Estimate(estimateId, estimateNumber, originUnLocode, destinationUnLocode,
                arrivalDeadline, cargoType, weightKg,
                candidates == null ? List.of() : candidates, status);
    }

    /**
     * 予約の内容が、この見積と食い違っていないか（受入基準 01-7・US04 の未達）。
     *
     * <p><strong>見積と違う条件で予約を受けると、見積の意味が消える。</strong>
     * 断るのではなく<strong>食い違いを言葉で返す</strong>——条件が変わること自体は
     * 業務として普通に起きる（荷主が数量を増やす）。営業担当者が気づいて
     * 荷主に確かめられればよい。
     *
     * @return 食い違っている項目。無ければ空
     */
    public List<String> differencesFrom(String bookingOrigin, String bookingDestination,
            LocalDate bookingDeadline, CargoType bookingCargoType, BigDecimal bookingWeightKg) {
        List<String> differences = new java.util.ArrayList<>();
        if (!originUnLocode.equals(bookingOrigin)) {
            differences.add("出発地");
        }
        if (!destinationUnLocode.equals(bookingDestination)) {
            differences.add("目的地");
        }
        if (!arrivalDeadline.equals(bookingDeadline)) {
            differences.add("到着期限");
        }
        if (cargoType != bookingCargoType) {
            differences.add("貨物種別");
        }
        // **桁数ではなく値で比べる。** 4200 と 4200.000 は同じ重量である
        if (bookingWeightKg == null || weightKg.compareTo(bookingWeightKg) != 0) {
            differences.add("重量");
        }
        return List.copyOf(differences);
    }

    public EstimateId estimateId() {
        return estimateId;
    }

    public EstimateNumber estimateNumber() {
        return estimateNumber;
    }

    public String originUnLocode() {
        return originUnLocode;
    }

    public String destinationUnLocode() {
        return destinationUnLocode;
    }

    public LocalDate arrivalDeadline() {
        return arrivalDeadline;
    }

    public CargoType cargoType() {
        return cargoType;
    }

    public BigDecimal weightKg() {
        return weightKg;
    }

    /** ルート候補（01-3）。**推奨順**。 */
    public List<RouteCandidate> candidates() {
        return candidates;
    }

    public EstimateStatus status() {
        return status;
    }
}
