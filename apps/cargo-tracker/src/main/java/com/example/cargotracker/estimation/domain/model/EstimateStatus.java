package com.example.cargotracker.estimation.domain.model;

import java.time.LocalDate;

/**
 * 見積の状態。
 *
 * <p><strong>期限切れは「画面を開いたときに判定する」</strong>
 * （`domain-model.md` のビジネスルール 7。ADR-019 と同じ形）。
 * 自動で走る仕組みを持たない本システムでは、**誰も開かない見積は誰も困らない**。
 */
public enum EstimateStatus {

    /** 作成済み。 */
    CREATED("作成済", "text-bg-primary"),

    /** 期限切れ。<strong>この見積では予約に進めない</strong>。 */
    EXPIRED("期限切れ", "text-bg-secondary");

    private final String displayName;
    private final String badgeClass;

    EstimateStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    /** 画面に出す表示名。 */
    public String displayName() {
        return displayName;
    }

    /** バッジ用 Bootstrap クラス（`ui_design.md` が正典）。 */
    public String badgeClass() {
        return badgeClass;
    }

    /**
     * 希望到着期限から見た状態（ビジネスルール 7）。
     *
     * <p><strong>期限当日は期限切れにしない。</strong> 当日着の便はまだ間に合う。
     * ADR-019（請求書の超過）と同じ境界である。
     *
     * @param arrivalDeadline 希望到着期限
     * @param today           業務のタイムゾーンの今日
     */
    public static EstimateStatus asOf(LocalDate arrivalDeadline, LocalDate today) {
        if (arrivalDeadline == null || today == null) {
            throw new IllegalArgumentException("期限と今日は必須です");
        }
        return today.isAfter(arrivalDeadline) ? EXPIRED : CREATED;
    }
}
