package com.example.routingms.domain.model;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 航海。特定の船が実施する一連の運送区間（US24・US25）。
 *
 * <p>経路候補算出（IT4 / US08）は、この集約が持つ「つながっているか」「運べるか」の判断を
 * そのまま使う。判断を呼び出し側に散らかすと、画面と経路探索で別々の答えを出すようになる。
 */
public final class Voyage {

    private final Long id;
    private final VoyageNumber voyageNumber;
    private final String vesselName;
    private final String carrierName;
    private final Set<CargoType> supportedCargoTypes;
    private final Schedule schedule;

    private Voyage(Long id, VoyageNumber voyageNumber, String vesselName, String carrierName,
            Set<CargoType> supportedCargoTypes, Schedule schedule) {
        this.id = id;
        this.voyageNumber = voyageNumber;
        this.vesselName = vesselName;
        this.carrierName = carrierName;
        this.supportedCargoTypes = supportedCargoTypes;
        this.schedule = schedule;
    }

    /**
     * 新規に受け入れる。ここでだけ入力を検査する。
     *
     * <p>船名と運送会社は必須。どの船かが分からないと、荷役の現場と問い合わせ窓口が貨物を
     * 追えない。対応できる貨物種別も必須で、空を許すと「登録は通るが検索に一切出てこない」
     * 航海ができる。原因が分からないまま「経路が見つからない」だけが残る。
     */
    public static Voyage register(VoyageNumber voyageNumber, String vesselName, String carrierName,
            Set<CargoType> supportedCargoTypes, Schedule schedule) {
        if (voyageNumber == null) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        if (isBlank(vesselName)) {
            throw new IllegalArgumentException("船名は必須です");
        }
        if (isBlank(carrierName)) {
            throw new IllegalArgumentException("運送会社は必須です");
        }
        if (supportedCargoTypes == null || supportedCargoTypes.isEmpty()) {
            throw new IllegalArgumentException("対応できる貨物種別を 1 つ以上選んでください");
        }
        if (schedule == null) {
            throw new IllegalArgumentException("航海スケジュールは必須です");
        }
        return new Voyage(null, voyageNumber, vesselName.trim(), carrierName.trim(),
                EnumSet.copyOf(supportedCargoTypes), schedule);
    }

    /**
     * 永続化された行から復元する。ここでは検査しない。
     *
     * <p>検査を後から足すと、その規則が無かったころの行が読めなくなる。
     */
    public static Voyage restore(Long id, VoyageNumber voyageNumber, String vesselName,
            String carrierName, Set<CargoType> supportedCargoTypes, Schedule schedule) {
        return new Voyage(id, voyageNumber, vesselName, carrierName,
                supportedCargoTypes == null || supportedCargoTypes.isEmpty()
                        ? Set.of() : EnumSet.copyOf(supportedCargoTypes),
                schedule);
    }

    /** その貨物種別を運べるか。危険物・冷凍は運べる船が限られる。 */
    public boolean supports(CargoType cargoType) {
        return cargoType != null && supportedCargoTypes.contains(cargoType);
    }

    /**
     * 出発地から目的地へ、この航海で運べるか。
     *
     * <p>寄港の<strong>順序</strong>で判断する。同じ港に寄ることと、その向きに運べることは別である。
     * 集合の包含で判断すると、逆向きの経路を提案してしまう。
     */
    public boolean connects(Location origin, Location destination) {
        if (origin == null || destination == null || origin.equals(destination)) {
            return false;
        }
        Optional<Integer> from = schedule.callingOrderOf(origin);
        Optional<Integer> to = schedule.callingOrderOf(destination);
        return from.isPresent() && to.isPresent() && from.get() < to.get();
    }

    public Optional<Instant> departureTime(Location location) {
        return schedule.departureTime(location);
    }

    public Optional<Instant> arrivalTime(Location location) {
        return schedule.arrivalTime(location);
    }

    public Optional<Long> id() {
        return Optional.ofNullable(id);
    }

    public VoyageNumber voyageNumber() {
        return voyageNumber;
    }

    public String vesselName() {
        return vesselName;
    }

    public String carrierName() {
        return carrierName;
    }

    public Set<CargoType> supportedCargoTypes() {
        return Set.copyOf(supportedCargoTypes);
    }

    public Schedule schedule() {
        return schedule;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
