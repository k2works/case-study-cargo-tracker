package com.example.cargotracker.simulation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 貨物種別と、その種別に要る付帯情報（US33 §受入基準 1）。 */
class CargoKindTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(CargoKind.class)
    @DisplayName("呼び名から引ける（業務の列挙と同じ語で送る）")
    void resolvesEveryKindByItsName(CargoKind kind) {
        assertThat(CargoKind.of(kind.name())).isEqualTo(kind);
    }

    @Test
    @DisplayName("知らない語は断る（打ち間違いを 422 に化けさせない）")
    void refusesUnknownNames() {
        // **黙って一般貨物に倒さない。** 倒すと、打ち間違えた種別のまま
        // 「成功した」実行が積み上がる。
        assertThatThrownBy(() -> CargoKind.of("DANGEROUS"))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("DANGEROUS");
    }

    @Test
    @DisplayName("付帯情報を書き換えても、次に引いたものは変わらない")
    void handsOutACopyOfTheDeclaration() {
        // **持ち出した地図を書き換えられて困るのは次の実行である。**
        // 予約の要求を組むときに putAll で足すので、共有すると汚れが残る。
        var declaration = CargoKind.HAZARDOUS.declaration();
        declaration.put("hazardImoClass", "9");

        assertThat(CargoKind.HAZARDOUS.declaration()).containsEntry("hazardImoClass", "3");
    }

    @Test
    @DisplayName("一般貨物には付帯情報が無い（付けると業務が断る）")
    void generalCargoCarriesNothing() {
        assertThat(CargoKind.GENERAL.declaration()).isEmpty();
    }
}
