package com.example.handlingms.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 通関申告（集約ルート。US29・UC21）。
 *
 * <p>ここが答えるのは<strong>引取を許してよいか</strong>である。通関が下りていない
 * 貨物を引き渡すと、税関との関係で会社が責任を負う。
 *
 * <p><strong>未決着（審査中・留置）は貨物あたり高々 1 件</strong>にする（[ADR-025] 決定 7）。
 * これを守るのは登録側だが、判断の材料（{@link #isSettled()}）は集約が持つ。
 * 「最新の 1 件」を暗黙に選ぶ実装にすると、同時に 2 件が審査中のとき何が「最新」かが
 * 決まらない。
 *
 * <p><strong>状態を変えるたびに履歴を積む</strong>（US29-8）。理由は必須であり、
 * 登録そのものも 1 行目として残る。
 */
public final class CustomsDeclaration {

    private final Long id;
    private final DeclarationNumber declarationNumber;
    private final CargoBookingId cargoBookingId;
    private final HandlingTrackingNumber trackingNumber;
    private final Instant declaredAt;
    private final CustomsStatus status;
    private final Instant clearedAt;
    private final String remarks;
    private final List<CustomsStatusChange> history;

    /**
     * <strong>引数の多さは、ここでは設計の合図ではない。</strong>
     *
     * <p>復元は保存された行をそのまま集約へ戻す操作であり、引数は集約の項目数そのもの
     * になる。減らすには項目を束ねる型を作ることになるが、それは復元のためだけの型で
     * あり、業務上の意味を持たない——<strong>検査を通すために語彙を増やすことになる</strong>。
     *
     * <p>外すのは復元だけである。人が値を渡す入口（登録・更新）には課したままにする
     * ——そちらは引数が増えたら本当に合図である。
     */
    @SuppressWarnings("java:S107")
    private CustomsDeclaration(Long id, DeclarationNumber declarationNumber,
            CargoBookingId cargoBookingId, HandlingTrackingNumber trackingNumber,
            Instant declaredAt, CustomsStatus status, Instant clearedAt, String remarks,
            List<CustomsStatusChange> history) {
        this.id = id;
        this.declarationNumber = declarationNumber;
        this.cargoBookingId = cargoBookingId;
        this.trackingNumber = trackingNumber;
        this.declaredAt = declaredAt;
        this.status = status;
        this.clearedAt = clearedAt;
        this.remarks = remarks;
        this.history = List.copyOf(history);
    }

    /**
     * 申告する（US29-1）。
     *
     * <p><strong>初期状態は審査中に決まっている。</strong>登録の時点で通関済を選べると、
     * 引取のガードが最初から素通りになる。
     */
    public static CustomsDeclaration declare(DeclarationNumber declarationNumber,
            CargoBookingId cargoBookingId, HandlingTrackingNumber trackingNumber,
            Instant declaredAt) {
        return declare(declarationNumber, cargoBookingId, trackingNumber, declaredAt, null, null);
    }

    /** 備考と登録者を伴う申告。 */
    public static CustomsDeclaration declare(DeclarationNumber declarationNumber,
            CargoBookingId cargoBookingId, HandlingTrackingNumber trackingNumber,
            Instant declaredAt, String remarks, String declaredBy) {
        if (declarationNumber == null || cargoBookingId == null || trackingNumber == null) {
            throw new IllegalArgumentException("申告番号・予約 ID・追跡番号は必須です");
        }
        if (declaredAt == null) {
            throw new IllegalArgumentException("申告日時は必須です");
        }
        // **登録も履歴に残す。**何も無い状態からは始まらない（from_status も NOT NULL）
        CustomsStatusChange declaration = new CustomsStatusChange(
                CustomsStatus.PENDING, CustomsStatus.PENDING,
                declaredBy == null || declaredBy.isBlank() ? "system" : declaredBy,
                declaredAt, "申告を登録しました");
        return new CustomsDeclaration(null, declarationNumber, cargoBookingId, trackingNumber,
                declaredAt, CustomsStatus.PENDING, null, remarks, List.of(declaration));
    }

    /**
     * 永続化された行から復元する。
     *
     * <p><strong>ここでは検査しない。</strong>列が無かったころの行や、規則が変わる前に
     * 入った行が読めなくなる（過去 take の教訓——不変条件の追加は既存行を壊す）。
     * 検査するのは新しく受け付けるときだけである。
     */
    @SuppressWarnings("java:S107")
    public static CustomsDeclaration restore(Long id, DeclarationNumber declarationNumber,
            CargoBookingId cargoBookingId, HandlingTrackingNumber trackingNumber,
            Instant declaredAt, CustomsStatus status, Instant clearedAt, String remarks,
            List<CustomsStatusChange> history) {
        return new CustomsDeclaration(id, declarationNumber, cargoBookingId, trackingNumber,
                declaredAt, status, clearedAt, remarks,
                history == null ? List.of() : history);
    }

    /**
     * 状態を更新する（US29-2）。
     *
     * <p><strong>理由は必須である。</strong>空で通すと、監査の履歴が「誰かが変えた」だけに
     * なる。あとから「なぜこの状態になったか」を読むのは、荷主に説明する担当者である。
     *
     * @throws IllegalArgumentException 理由が空のとき、またはいまと同じ状態のとき
     */
    public CustomsDeclaration updateStatus(CustomsStatus newStatus, String changedBy,
            String reason, Instant changedAt) {
        if (newStatus == null) {
            throw new IllegalArgumentException("新しい通関状態を選んでください");
        }
        if (newStatus == status) {
            throw new IllegalArgumentException(
                    "すでに%sです".formatted(status.label()));
        }
        // CustomsStatusChange が理由・変更者・日時を検査する。ここで写さない
        CustomsStatusChange change =
                new CustomsStatusChange(status, newStatus, changedBy, changedAt, reason);
        List<CustomsStatusChange> appended = new ArrayList<>(history);
        appended.add(change);

        return new CustomsDeclaration(id, declarationNumber, cargoBookingId, trackingNumber,
                declaredAt, newStatus,
                newStatus == CustomsStatus.CLEARED ? changedAt : null,
                remarks, appended);
    }

    /**
     * 引き取ってよいか（US29-3）。
     *
     * <p><strong>通関済のときだけ真である。</strong>「審査中でなければ通す」形にすると、
     * 留置・不可の貨物まで引き取れる。ガードは<strong>通してよい 1 つ</strong>を見る。
     */
    public boolean isCleared() {
        return status == CustomsStatus.CLEARED;
    }

    /** これ以上の判断を待たなくてよいか（[ADR-025] 決定 7）。 */
    public boolean isSettled() {
        return status.settled();
    }

    /**
     * 留置のまま所定の日数を超えたか（US29-6）。
     *
     * <p><strong>数えるのは最新の留置遷移からである</strong>（`data-model.md` の注）。
     * 申告日から数えると、いったん通関して留め直された申告が、留め直した初日から
     * 「3 日超」と判定される。督促は「いま何日留め置かれているか」で決める。
     *
     * <p><strong>日付単位で比べる。</strong>時刻まで見ると、同じ日の朝と夕方で結果が
     * 変わる。業務の暦は{@code zone}（業務タイムゾーン）で決める——UTC で判断すると、
     * 時差の分だけ督促が早まったり遅れたりする。
     *
     * @param today 業務上の今日。**呼び出し側が注入した Clock から作る**
     * @param zone 業務タイムゾーン
     * @param thresholdDays この日数を<strong>超えたら</strong>対象（3 日ちょうどは対象外）
     */
    public boolean isHeldOverdue(LocalDate today, ZoneId zone, int thresholdDays) {
        return heldDays(today, zone).filter(days -> days > thresholdDays).isPresent();
    }

    /** 留置になってからの日数。留置でなければ空。 */
    public Optional<Long> heldDays(LocalDate today, ZoneId zone) {
        if (status != CustomsStatus.HELD) {
            return Optional.empty();
        }
        return latestHeldAt()
                .map(heldAt -> ChronoUnit.DAYS.between(heldAt.atZone(zone).toLocalDate(), today));
    }

    /** 最新の「留置になった」日時。履歴に無ければ申告日時（復元した古い行のため）。 */
    private Optional<Instant> latestHeldAt() {
        for (int index = history.size() - 1; index >= 0; index--) {
            CustomsStatusChange change = history.get(index);
            if (change.toStatus() == CustomsStatus.HELD) {
                return Optional.of(change.changedAt());
            }
        }
        return Optional.ofNullable(declaredAt);
    }

    public Long id() {
        return id;
    }

    public DeclarationNumber declarationNumber() {
        return declarationNumber;
    }

    public CargoBookingId cargoBookingId() {
        return cargoBookingId;
    }

    public HandlingTrackingNumber trackingNumber() {
        return trackingNumber;
    }

    public Instant declaredAt() {
        return declaredAt;
    }

    public CustomsStatus status() {
        return status;
    }

    public Optional<Instant> clearedAt() {
        return Optional.ofNullable(clearedAt);
    }

    public Optional<String> remarks() {
        return Optional.ofNullable(remarks);
    }

    /** 状態変更の履歴（US29-8）。**追記しかしない**。 */
    public List<CustomsStatusChange> history() {
        return history;
    }
}
