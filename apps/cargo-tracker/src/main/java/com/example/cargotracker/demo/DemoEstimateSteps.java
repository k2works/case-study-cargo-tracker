package com.example.cargotracker.demo;

import com.example.cargotracker.estimation.application.internal.commandservices
        .CreateEstimateCommandService;
import com.example.cargotracker.estimation.domain.model.EstimationCargoType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** 動作確認用の見積を用意する（マニュアル 12）。 */
@Component
class DemoEstimateSteps {

    private final CreateEstimateCommandService estimate;
    private final Clock clock;

    DemoEstimateSteps(CreateEstimateCommandService estimate, Clock clock) {
        this.estimate = estimate;
        this.clock = clock;
    }

    void install() {
        create("JPOSA", "USLAX", 30, EstimationCargoType.GENERAL, "1200.5");
        create("JPYOK", "DEHAM", 45, EstimationCargoType.REFRIGERATED, "800");
        // **候補が 0 件の見積も要る。** 「便がありません」の画面は、
        // 候補が出る画面と同じくらい読者が出会う
        create("NLRTM", "SGSIN", 5, EstimationCargoType.GENERAL, "500");
    }

    private void create(
            String origin, String destination, int days,
            EstimationCargoType type, String weight) {
        estimate.create(new CreateEstimateCommandService.Request(
                origin, destination, LocalDate.now(clock).plusDays(days),
                type, new BigDecimal(weight), null));
    }
}
