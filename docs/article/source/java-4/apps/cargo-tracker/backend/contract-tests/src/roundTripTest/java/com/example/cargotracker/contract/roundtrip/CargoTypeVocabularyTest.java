package com.example.cargotracker.contract.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 貨物種別の語彙が契約の両端でつながっているか（IT14 負債 2）。
 *
 * <p><b>契約の語彙は Booking のもの</b>で統一する（{@code GENERAL} /
 * {@code HAZARDOUS} / {@code REFRIGERATED}）。Routing だけ冷凍を {@code REEFER}
 * と呼ぶので、受け側が境界で翻訳する。</p>
 *
 * <p><b>翻訳が欠けていても、両 BC の単体テストは緑になる。</b> どちらも自分の
 * 語彙しか見ないからである。IT13 まで実際に欠けていて、冷凍の予約は経路候補を
 * 1 件も見られなかった（routingms が 422 で断っていた）。ここは両方の列挙型を
 * 同時に見られる唯一の場所なので、<b>Booking に種別を足したら赤になる</b>形で
 * 固定する。</p>
 */
class CargoTypeVocabularyTest {

    @Test
    @DisplayName("Booking のすべての貨物種別が Routing の列挙型に翻訳できる")
    void everyBookingCargoTypeTranslatesToRouting() {
        // **名簿を手書きしない。** 列挙から回すので、足した値は名乗り出なくても対象になる。
        assertThat(Arrays.stream(CargoType.values()).map(Enum::name))
                .allSatisfy(name -> assertThat(
                        com.example.cargotracker.routing.domain.model.valueobjects.CargoType
                                .fromContractName(name))
                        .as("契約の %s を Routing が解釈できる", name)
                        .isNotNull());
    }

    @Test
    @DisplayName("冷凍は REEFER に翻訳される（素通りさせると経路候補が 0 件にもならず 422 になる）")
    void refrigeratedBecomesReefer() {
        assertThat(com.example.cargotracker.routing.domain.model.valueobjects.CargoType
                .fromContractName("REFRIGERATED"))
                .isEqualTo(com.example.cargotracker.routing.domain.model.valueobjects
                        .CargoType.REEFER);
    }

    @Test
    @DisplayName("知らない名前は断る（名簿に載っていないものを通さない）")
    void unknownNameIsRejected() {
        assertThatThrownBy(() -> com.example.cargotracker.routing.domain.model.valueobjects
                .CargoType.fromContractName("LIVESTOCK"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
