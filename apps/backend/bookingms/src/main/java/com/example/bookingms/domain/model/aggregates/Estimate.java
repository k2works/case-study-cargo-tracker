package com.example.bookingms.domain.model.aggregates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.example.bookingms.domain.model.valueobjects.CargoType;
import com.example.bookingms.domain.model.valueobjects.EstimateId;
import com.example.bookingms.domain.model.valueobjects.EstimateNumber;
import com.example.bookingms.domain.model.valueobjects.EstimateRequirements;
import com.example.bookingms.domain.model.valueobjects.EstimateStatus;
import com.example.bookingms.domain.model.valueobjects.RouteCandidate;

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
    /** 荷主の輸送要件（5 項目）。**いつも揃って動く。** */
    private final EstimateRequirements requirements;
    private final List<RouteCandidate> candidates;
    private final EstimateStatus status;

    private Estimate(EstimateId estimateId, EstimateNumber estimateNumber,
            EstimateRequirements requirements, List<RouteCandidate> candidates,
            EstimateStatus status) {
        this.estimateId = estimateId;
        this.estimateNumber = estimateNumber;
        this.requirements = requirements;
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
            EstimateRequirements requirements, List<RouteCandidate> candidates) {
        if (estimateId == null || estimateNumber == null) {
            throw new IllegalArgumentException("見積の識別子と見積番号を指定してください");
        }
        if (requirements == null) {
            throw new IllegalArgumentException("輸送要件を指定してください");
        }
        return new Estimate(estimateId, estimateNumber, requirements,
                candidates == null ? List.of() : candidates, EstimateStatus.CREATED);
    }

    /**
     * 永続化された行から復元する。
     *
     * <p><strong>ここでは検査しない</strong>（新しい不変条件は既存行を壊す）。
     */
    public static Estimate restore(EstimateId estimateId, EstimateNumber estimateNumber,
            EstimateRequirements requirements, List<RouteCandidate> candidates,
            EstimateStatus status) {
        return new Estimate(estimateId, estimateNumber, requirements,
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
    public List<String> differencesFrom(EstimateRequirements booking) {
        return requirements.differencesFrom(booking);
    }

    public EstimateId estimateId() {
        return estimateId;
    }

    public EstimateNumber estimateNumber() {
        return estimateNumber;
    }

    /** 荷主の輸送要件（5 項目）。 */
    public EstimateRequirements requirements() {
        return requirements;
    }

    public String originUnLocode() {
        return requirements.originUnLocode();
    }

    public String destinationUnLocode() {
        return requirements.destinationUnLocode();
    }

    public LocalDate arrivalDeadline() {
        return requirements.arrivalDeadline();
    }

    public CargoType cargoType() {
        return requirements.cargoType();
    }

    public BigDecimal weightKg() {
        return requirements.weightKg();
    }

    /** ルート候補（01-3）。**推奨順**。 */
    public List<RouteCandidate> candidates() {
        return candidates;
    }

    public EstimateStatus status() {
        return status;
    }
}
