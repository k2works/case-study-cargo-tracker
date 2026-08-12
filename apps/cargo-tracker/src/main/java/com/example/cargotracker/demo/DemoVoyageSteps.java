package com.example.cargotracker.demo;

import static com.example.cargotracker.demo.DemoActors.ACTOR;

import com.example.cargotracker.routing.application.internal.commandservices.RegisterVoyageCommandService;
import com.example.cargotracker.routing.domain.model.CarrierMovement;
import com.example.cargotracker.routing.domain.model.CarrierName;
import com.example.cargotracker.routing.domain.model.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.RoutingWeight;
import com.example.cargotracker.routing.domain.model.Schedule;
import com.example.cargotracker.routing.domain.model.VesselName;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.shared.domain.model.Location;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
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
@Component
class DemoVoyageSteps {

    private final RegisterVoyageCommandService registerVoyage;
    private final Clock clock;

    DemoVoyageSteps(RegisterVoyageCommandService registerVoyage, Clock clock) {
        this.registerVoyage = registerVoyage;
        this.clock = clock;
    }

    void install() {
        voyage("V0001", "さくら丸", "日本海運",
                Set.of(RoutingCargoType.GENERAL, RoutingCargoType.REFRIGERATED),
                List.of(leg("JPOSA", "USLAX", 3, 17)));
        voyage("V0002", "みらい丸", "アジア航路",
                Set.of(RoutingCargoType.GENERAL),
                List.of(leg("JPYOK", "SGSIN", 4, 11), leg("SGSIN", "DEHAM", 13, 34)));
        voyage("V0003", "はやぶさ丸", "欧州ライン",
                Set.of(RoutingCargoType.GENERAL, RoutingCargoType.HAZARDOUS),
                List.of(leg("JPKOB", "NLRTM", 5, 33)));
        voyage("V0004", "ふじ丸", "北欧ライン",
                Set.of(RoutingCargoType.GENERAL, RoutingCargoType.REFRIGERATED),
                List.of(leg("JPYOK", "DEHAM", 6, 36)));
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
