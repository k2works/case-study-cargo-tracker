package com.example.handlingms.domain.model.aggregates;

import com.example.handlingms.domain.model.valueobjects.CargoBookingId;
import com.example.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.handlingms.domain.model.valueobjects.ConsigneeConfirmation;
import com.example.handlingms.domain.model.valueobjects.HandlingType;
import com.example.handlingms.domain.model.valueobjects.HandlingVoyageNumber;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.Optional;

/**
 * 荷役作業の記録（US15・US16・[ADR-023]）。
 *
 * <p><strong>実際に起きた作業の記録である。</strong>あとから「無かったこと」にはできない。
 * だから、拒む判断は「記録そのものが成り立たない」ものに限る——航海番号のない積込は
 * どの船に載せたか分からず、荷受人の確認のない引取は誰に渡したか分からない。
 *
 * <p>一方で<strong>予定と違う場所の作業は拒まない</strong>（決定 3）。現場ではすでに
 * 終わっており、拒めば実際に起きたことがどこにも残らない。予定外だったことを行に残す。
 */
public final class HandlingActivity {

    private final Long id;
    private final CargoBookingId bookingId;
    private final HandlingType type;
    private final Location location;
    private final Instant completionTime;
    private final String operatorName;
    private final HandlingVoyageNumber voyageNumber;
    private final ConsigneeConfirmation consigneeConfirmation;
    private final boolean offRoute;

    // S107（引数が多い）: 復元は永続化された行の写しであり、列数がそのまま現れる。
    // まとめると復元の意味が薄れる（テスト戦略の Quality Gate 例外表に記載）
    @SuppressWarnings("java:S107")
    private HandlingActivity(Long id, CargoBookingId bookingId, HandlingType type,
            Location location, Instant completionTime, String operatorName,
            HandlingVoyageNumber voyageNumber, ConsigneeConfirmation consigneeConfirmation,
            boolean offRoute) {
        this.id = id;
        this.bookingId = bookingId;
        this.type = type;
        this.location = location;
        this.completionTime = completionTime;
        this.operatorName = operatorName;
        this.voyageNumber = voyageNumber;
        this.consigneeConfirmation = consigneeConfirmation;
        this.offRoute = offRoute;
    }

    /**
     * 荷役作業を記録する。
     *
     * <p>種別ごとの要件は<strong>種別そのものに問う</strong>（決定 1）。ここで
     * {@code if (type == LOAD)} と書くと、種別が増えたときに書き換える場所が散らばる。
     *
     * @param cargo ACL 経由で取った貨物の写し
     * @param type 荷役の種別
     * @param location 作業場所
     * @param completionTime 作業日時
     * @param operatorName 作業者。<strong>誰が記録したか分からない記録は監査に使えない</strong>
     * @param voyageNumber 航海番号。積込・荷降しでは必須
     * @param consigneeConfirmation 荷受人の確認。引取では必須（決定 4）
     * @throws IllegalArgumentException 種別の要件を満たしていないとき
     */
    @SuppressWarnings("java:S107")
    public static HandlingActivity register(CargoSnapshot cargo, HandlingType type,
            Location location, Instant completionTime, String operatorName,
            HandlingVoyageNumber voyageNumber, ConsigneeConfirmation consigneeConfirmation) {
        requireNotNull(cargo, "貨物");
        requireNotNull(type, "荷役の種別");
        requireNotNull(location, "作業場所");
        requireNotNull(completionTime, "作業日時");
        if (operatorName == null || operatorName.isBlank()) {
            throw new IllegalArgumentException("作業者は必須です");
        }
        if (type.requiresVoyageNumber() && voyageNumber == null) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        if (type.requiresConsigneeConfirmation() && consigneeConfirmation == null) {
            throw new IllegalArgumentException("荷受人の確認は必須です");
        }

        // 予定外でも拒まない（決定 3）。判定は貨物の写しが持つ——ここで書き直すと、
        // 本番が間違っていても検査だけが正しくなる
        boolean offRoute = cargo.isOffRoute(type, location.unLocode());

        return new HandlingActivity(null, CargoBookingId.of(cargo.bookingId()), type, location,
                completionTime, operatorName.trim(), voyageNumber, consigneeConfirmation,
                offRoute);
    }

    /**
     * 永続化された行から復元する。
     *
     * <p><strong>ここでは検査しない。</strong>要件が無かったころの行が読めなくなる。
     * 検査するのは新しく受け付けるときだけである。
     */
    @SuppressWarnings("java:S107")
    public static HandlingActivity restore(Long id, CargoBookingId bookingId, HandlingType type,
            Location location, Instant completionTime, String operatorName,
            HandlingVoyageNumber voyageNumber, ConsigneeConfirmation consigneeConfirmation,
            boolean offRoute) {
        return new HandlingActivity(id, bookingId, type, location, completionTime, operatorName,
                voyageNumber, consigneeConfirmation, offRoute);
    }

    private static void requireNotNull(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + "は必須です");
        }
    }

    public Long id() {
        return id;
    }

    public CargoBookingId bookingId() {
        return bookingId;
    }

    public HandlingType type() {
        return type;
    }

    public Location location() {
        return location;
    }

    public Instant completionTime() {
        return completionTime;
    }

    public String operatorName() {
        return operatorName;
    }

    /** 航海番号。受領・引取では空を返す。 */
    public Optional<HandlingVoyageNumber> voyageNumber() {
        return Optional.ofNullable(voyageNumber);
    }

    /** 荷受人の確認。引取以外では空を返す。 */
    public Optional<ConsigneeConfirmation> consigneeConfirmation() {
        return Optional.ofNullable(consigneeConfirmation);
    }

    /**
     * 予定と違う場所での作業だったか（決定 3）。
     *
     * <p>行に残すのは、誤配の検知（US28）で<strong>過去の作業を判定し直さない</strong>
     * ためである。判定には旅程が要り、旅程は変わりうる。組み直したあとに過去の作業を
     * 判定し直すと、<strong>そのときは予定外だった作業が「予定どおり」に化ける</strong>。
     */
    public boolean offRoute() {
        return offRoute;
    }
}
