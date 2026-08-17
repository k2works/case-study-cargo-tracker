package com.example.cargotracker.demo;

import static com.example.cargotracker.demo.DemoActors.ACTOR;

import com.example.cargotracker.routing.application.internal.commandservices.RegisterVoyageCommandService;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierName;
import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingWeight;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 動作確認用の便を用意する（マニュアル 05）。
 *
 * <p><strong>便が無ければ経路が付かず、予約は確定できず、追跡も荷役も請求もすべて空になる</strong>
 * —— 1 つの前提が外れると章が 5 つ空になる。
 *
 * <p><strong>出発日を固定日にしない。</strong> 固定日にすると時間の経過とともに過去の便になり、
 * 「もう出港した便が検索で出てくる」画面がマニュアルに残る。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Component
class DemoVoyageSteps {

    // **投入が前提にする港と便。**
    //
    // ここを変えると、予約・追跡・荷役・請求の章がまとめて空になる ——
    // **1 つの前提が外れると章が 5 つ空になる**。
    // 前提であることをコードに現すため、投入の側から参照する定数として置く。

    /** 大阪。 */
    static final String OSAKA = "JPOSA";

    /** ロサンゼルス。 */
    static final String LOS_ANGELES = "USLAX";

    /** 神戸。 */
    static final String KOBE = "JPKOB";

    /** ロッテルダム。 */
    static final String ROTTERDAM = "NLRTM";

    /** 横浜。 */
    static final String YOKOHAMA = "JPYOK";

    /** ハンブルク。 */
    static final String HAMBURG = "DEHAM";

    /** シンガポール。 */
    static final String SINGAPORE = "SGSIN";

    /** OSAKA → ロサンゼルスの直行便（一般・冷凍）。 */
    static final String DIRECT_VOYAGE = "V0001";

    /** KOBE → ロッテルダムの便（一般・危険物）。 */
    static final String HAZARDOUS_VOYAGE = "V0003";

    private final RegisterVoyageCommandService registerVoyage;
    private final Clock clock;

    DemoVoyageSteps(RegisterVoyageCommandService registerVoyage, Clock clock) {
        this.registerVoyage = registerVoyage;
        this.clock = clock;
    }

    void install() {
        voyage(DIRECT_VOYAGE, "さくら丸", "日本海運",
                Set.of(RoutingCargoType.GENERAL, RoutingCargoType.REFRIGERATED),
                List.of(leg(OSAKA, LOS_ANGELES, 3, 17)));
        voyage("V0002", "みらい丸", "アジア航路",
                Set.of(RoutingCargoType.GENERAL),
                List.of(leg(YOKOHAMA, SINGAPORE, 4, 11), leg(SINGAPORE, HAMBURG, 13, 34)));
        voyage(HAZARDOUS_VOYAGE, "はやぶさ丸", "欧州ライン",
                Set.of(RoutingCargoType.GENERAL, RoutingCargoType.HAZARDOUS),
                List.of(leg(KOBE, ROTTERDAM, 5, 33)));
        voyage("V0004", "ふじ丸", "北欧ライン",
                Set.of(RoutingCargoType.GENERAL, RoutingCargoType.REFRIGERATED),
                List.of(leg(YOKOHAMA, HAMBURG, 6, 36)));
    }

    /**
     * 便を 1 つ登録する（US24）。
     *
     * <p><strong>自動実行はこの入口で毎回ちがう便を作る。</strong> 上の 4 便に
     * 相乗りさせると、<strong>割り当てるたびに空き容量が減り、いずれ経路候補が
     * 0 件になる</strong>（{@code Voyage.hasCapacityFor} は割当済みを差し引く）。
     * デモを繰り返すほど動かなくなる形は、動作確認の道具として成り立たない。
     *
     * @param number         航海番号（<strong>既存と重ならないこと</strong>）
     * @param cargoTypes     積める貨物種別
     * @param origin         出発港
     * @param destination    到着港
     * @param departsInDays  何日後に出港するか
     * @param arrivesInDays  何日後に到着するか（<strong>到着期限より前でなければ選べない</strong>）
     */
    void register(
            String number, Set<RoutingCargoType> cargoTypes,
            String origin, String destination, int departsInDays, int arrivesInDays) {
        voyage(number, "デモ丸", "デモ海運", cargoTypes,
                List.of(leg(origin, destination, departsInDays, arrivesInDays)));
    }

    private CarrierMovement leg(String from, String to, int departsInDays, int arrivesInDays) {
        return CarrierMovement.of(
                Location.of(from), Location.of(to),
                clock.instant().plus(Duration.ofDays(departsInDays)),
                clock.instant().plus(Duration.ofDays(arrivesInDays)));
    }

    private void voyage(
            String number, String vessel, String carrier,
            Set<RoutingCargoType> cargoTypes, List<CarrierMovement> movements) {
        registerVoyage.register(new RegisterVoyageCommand(
                new VoyageNumber(number), new VesselName(vessel), new CarrierName(carrier),
                Schedule.of(movements), cargoTypes,
                RoutingWeight.ofKilograms(new BigDecimal("50000"))), ACTOR);
    }
}
