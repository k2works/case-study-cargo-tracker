package com.example.cargotracker.routing;

import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageLeg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Voyage 集約")
class VoyageTest {

    @Test
    @DisplayName("有効な値で Voyage を生成できる")
    void voyage_生成_正常系() {
        var leg = new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15));
        var voyage = new Voyage(
            "SG001", "Japan Freight Lines",
            Set.of(CargoType.GENERAL, CargoType.REFRIGERATED),
            List.of(leg)
        );

        assertThat(voyage.voyageNumber()).isEqualTo("SG001");
        assertThat(voyage.carrierName()).isEqualTo("Japan Freight Lines");
        assertThat(voyage.supportedCargoTypes()).containsExactlyInAnyOrder(
            CargoType.GENERAL, CargoType.REFRIGERATED);
        assertThat(voyage.legs()).hasSize(1);
    }

    @Test
    @DisplayName("voyageNumber が null の場合 IllegalArgumentException をスローする")
    void voyage_voyageNumber_null() {
        var leg = new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15));
        Set<CargoType> supportedCargoTypes = Set.of(CargoType.GENERAL);
        List<VoyageLeg> legs = List.of(leg);

        assertThatThrownBy(() -> new Voyage(null, "Carrier", supportedCargoTypes, legs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("voyageNumber");
    }

    @Test
    @DisplayName("voyageNumber が空文字の場合 IllegalArgumentException をスローする")
    void voyage_voyageNumber_blank() {
        var leg = new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15));
        Set<CargoType> supportedCargoTypes = Set.of(CargoType.GENERAL);
        List<VoyageLeg> legs = List.of(leg);

        assertThatThrownBy(() -> new Voyage("  ", "Carrier", supportedCargoTypes, legs))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("carrierName が null の場合 IllegalArgumentException をスローする")
    void voyage_carrierName_null() {
        var leg = new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15));
        Set<CargoType> supportedCargoTypes = Set.of(CargoType.GENERAL);
        List<VoyageLeg> legs = List.of(leg);

        assertThatThrownBy(() -> new Voyage("SG001", null, supportedCargoTypes, legs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("carrierName");
    }

    @Test
    @DisplayName("supportedCargoTypes が空の場合 IllegalArgumentException をスローする")
    void voyage_supportedCargoTypes_empty() {
        var leg = new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15));
        List<VoyageLeg> legs = List.of(leg);
        Set<CargoType> supportedCargoTypes = Set.of();

        assertThatThrownBy(() -> new Voyage("SG001", "Carrier", supportedCargoTypes, legs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("supportedCargoTypes");
    }

    @Test
    @DisplayName("legs が空の場合 IllegalArgumentException をスローする")
    void voyage_legs_empty() {
        Set<CargoType> supportedCargoTypes = Set.of(CargoType.GENERAL);
        List<VoyageLeg> legs = List.of();

        assertThatThrownBy(() -> new Voyage("SG001", "Carrier", supportedCargoTypes, legs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("legs");
    }

    @Test
    @DisplayName("supports() は supportedCargoTypes に含まれる種別で true を返す")
    void voyage_supports_true() {
        var leg = new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15));
        var voyage = new Voyage("SG001", "Carrier",
            Set.of(CargoType.GENERAL, CargoType.HAZARDOUS), List.of(leg));

        assertThat(voyage.supports(CargoType.GENERAL)).isTrue();
        assertThat(voyage.supports(CargoType.HAZARDOUS)).isTrue();
        assertThat(voyage.supports(CargoType.REFRIGERATED)).isFalse();
    }

    @Test
    @DisplayName("複数 leg を持つ Voyage を生成できる")
    void voyage_複数leg() {
        var leg1 = new VoyageLeg("JPTYO", "KRPUS",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));
        var leg2 = new VoyageLeg("KRPUS", "SGSIN",
            LocalDate.of(2026, 6, 6), LocalDate.of(2026, 6, 15));
        var voyage = new Voyage("SG002", "Carrier",
            Set.of(CargoType.GENERAL), List.of(leg1, leg2));

        assertThat(voyage.legs()).hasSize(2);
        assertThat(voyage.legs().get(0).originLocode()).isEqualTo("JPTYO");
        assertThat(voyage.legs().get(1).destinationLocode()).isEqualTo("SGSIN");
    }
}
