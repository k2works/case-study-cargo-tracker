package com.example.cargotracker.simulation.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 便の用意（**必要なデータ準備もシナリオに含める**）。
 *
 * <p><b>本物の HTTP を通す。</b> 段取りは {@link GatewayStepsTestSupport} が持つ。</p>
 */
class GatewayVoyagesTest extends GatewayStepsTestSupport {

    private static final Map<StepKind, String> NOTHING_PRODUCED = Map.of();

    private static ScenarioInput input(Scenario scenario, String cargoType) {
        return new ScenarioInput(scenario, "JPTYO", "USNYC", cargoType,
                BigDecimal.valueOf(1000), 120);
    }

    /** 既にある便（区間と受け入れ種別つき）。 */
    private void existingVoyage(String number, String departure, String arrival,
            String... acceptedCargoTypes) {
        StringBuilder types = new StringBuilder();
        for (int i = 0; i < acceptedCargoTypes.length; i++) {
            types.append(i == 0 ? "" : ",").append('"').append(acceptedCargoTypes[i]).append('"');
        }
        responses.put("/api/v1/routing/voyages",
                "{\"items\":[{\"voyageNumber\":\"" + number + "\",\"acceptedCargoTypes\":["
                        + types + "],\"movements\":[{\"departureUnLocode\":\"" + departure
                        + "\",\"arrivalUnLocode\":\"" + arrival + "\"}]}]}");
    }

    @Test
    @DisplayName("便が 1 本も無い環境では、その輸送に要る便を登録する")
    void registersTheVoyageWhenNothingIsAvailable() throws IOException {
        responses.put("/api/v1/routing/voyages", "{\"items\":[]}");
        var api = start(input(Scenario.STANDARD, "GENERAL"));

        var result = api.execute(StepKind.PREPARE_VOYAGES, NOTHING_PRODUCED);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.producedId()).startsWith("V-SIM-");
        assertThat(bodies).anyMatch(body -> body.startsWith("POST /api/v1/routing/voyages ")
                && body.contains("\"departureUnLocode\":\"JPTYO\"")
                && body.contains("\"arrivalUnLocode\":\"USNYC\""));
    }

    @Test
    @DisplayName("同じ区間を運べる便があれば足さない（航海の一覧を埋めない）")
    void reusesAVoyageThatAlreadyServesTheLeg() throws IOException {
        existingVoyage("V-EXISTING", "JPTYO", "USNYC", "GENERAL");
        var api = start(input(Scenario.STANDARD, "GENERAL"));

        var result = api.execute(StepKind.PREPARE_VOYAGES, NOTHING_PRODUCED);

        assertThat(result.succeeded()).isTrue();
        // **何も足さなかったことも記録に残す。** 空だと「用意したのか、既に
        // あったのか」を読む人が区別できない。
        assertThat(result.producedId()).isEqualTo("既にある便を使います");
        assertThat(bodies).noneMatch(body -> body.startsWith("POST /api/v1/routing/voyages "));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = com.example.cargotracker.simulation.domain.model.valueobjects.CargoKind
            .class, names = {"HAZARDOUS", "REFRIGERATED"})
    @DisplayName("その貨物を運べない便は「ある」と数えない（候補 0 件で止まらせない）")
    void doesNotCountAVoyageThatRefusesOurCargo(
            com.example.cargotracker.simulation.domain.model.valueobjects.CargoKind kind)
            throws IOException {
        // 区間は同じだが、一般貨物しか運ばない便。
        existingVoyage("V-GENERAL-ONLY", "JPTYO", "USNYC", "GENERAL");
        var api = start(input(Scenario.STANDARD, kind.name()));

        var result = api.execute(StepKind.PREPARE_VOYAGES, NOTHING_PRODUCED);

        assertThat(result.producedId()).startsWith("V-SIM-");
        assertThat(bodies).anyMatch(body -> body.startsWith("POST /api/v1/routing/voyages ")
                && body.contains("\"acceptedCargoTypes\":[\"" + kind.name() + "\"]"));
    }

    @Test
    @DisplayName("受け入れ種別を宣言していない便は一般貨物だけを運ぶ（不変条件 4）")
    void treatsAnEmptyDeclarationAsGeneralOnly() throws IOException {
        existingVoyage("V-NO-DECLARATION", "JPTYO", "USNYC");
        var api = start(input(Scenario.STANDARD, "GENERAL"));

        assertThat(api.execute(StepKind.PREPARE_VOYAGES, NOTHING_PRODUCED).producedId())
                .isEqualTo("既にある便を使います");
    }

    @Test
    @DisplayName("US35 §3: 誤配は、経路外の港から目的地へ運べる便も用意する")
    void alsoPreparesTheLegTheMisrouteWillBeRebuiltFrom() throws IOException {
        responses.put("/api/v1/routing/voyages", "{\"items\":[]}");
        var api = start(input(Scenario.MISROUTE, "GENERAL"));

        var result = api.execute(StepKind.PREPARE_VOYAGES, NOTHING_PRODUCED);

        // **組み直しは現在地から行う。** 経路外の港から目的地へ運べる便が無いと、
        // 「組み直す先がない」で止まる。
        assertThat(result.producedId().split(",")).hasSize(2);
        assertThat(bodies).anyMatch(body -> body.startsWith("POST /api/v1/routing/voyages ")
                && body.contains("\"departureUnLocode\":\"SGSIN\"")
                && body.contains("\"arrivalUnLocode\":\"USNYC\""));
    }

    @Test
    @DisplayName("航海の一覧が読めなければ、その理由を残す（黙って登録しない）")
    void keepsTheReasonWhenTheVoyageListCannotBeRead() throws IOException {
        statuses.put("/api/v1/routing/voyages", 500);
        responses.put("/api/v1/routing/voyages", "{\"message\":\"航海が読めません\"}");
        var api = start(input(Scenario.STANDARD, "GENERAL"));

        var result = api.execute(StepKind.PREPARE_VOYAGES, NOTHING_PRODUCED);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).contains("航海が読めません");
    }
}
