package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.shared.domain.location.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 見積と予約の違い（US01・正典の不変条件 3）。
 *
 * <p><b>比較は 1 か所</b>（{@link QuotationTerms}）。集約の {@code diffAgainst} と
 * 予約の受付（{@code QuotationDiff}）の両方がここを呼ぶので、ここが正しければ
 * 両方が正しい。<b>逆に、ここを迂回して比較を書いたら意味が無い。</b></p>
 *
 * <p><b>断るための型ではない。</b> 違いは項目名で知らせるだけで、予約そのものは
 * 通す——荷主の事情は見積のあとで変わる。</p>
 */
class QuotationDiffTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);

    private static QuotationTerms quoted() {
        return new QuotationTerms("JPTYO", "USNYC", DEADLINE, "GENERAL",
                new BigDecimal("1200"));
    }

    private static BookCargoCommand booking(String origin, String destination,
            LocalDate deadline, CargoType cargoType, String weightKg) {
        return new BookCargoCommand("B-1", "SHP-000001",
                new CargoSpecification(cargoType, Weight.ofKilograms(weightKg),
                        new Dimensions(new BigDecimal("120"), new BigDecimal("80"),
                                new BigDecimal("100")),
                        10, "自動車部品",
                        cargoType == CargoType.HAZARDOUS
                                ? new HazardousDeclaration("3", "UN1263") : null,
                        cargoType == CargoType.REFRIGERATED
                                ? new TemperatureRequirement(new BigDecimal("-20"),
                                        new BigDecimal("-5")) : null),
                new RouteSpecification(Location.of(origin), Location.of(destination), deadline),
                "sales01");
    }

    @Test
    @DisplayName("見積どおりなら違いは出ない")
    void reportsNothingForAMatchingBooking() {
        assertThat(quoted().differencesAgainst(QuotationTerms.of(
                booking("JPTYO", "USNYC", DEADLINE, CargoType.GENERAL, "1200"))))
                .isEmpty();
    }

    @Test
    @DisplayName("1200 と 1200.00 を「違う」と言わない（毎回出ると読まれなくなる）")
    void comparesWeightAsANumber() {
        assertThat(quoted().differencesAgainst(QuotationTerms.of(
                booking("JPTYO", "USNYC", DEADLINE, CargoType.GENERAL, "1200.00"))))
                .as("BigDecimal の equals は桁数まで見るので、そのまま比べると毎回違うと出る")
                .isEmpty();
    }

    @Test
    @DisplayName("項目ごとに「何から何へ」を返す")
    void reportsEachDifferentTerm() {
        var differences = quoted().differencesAgainst(QuotationTerms.of(
                booking("JPOSA", "USLAX", LocalDate.of(2026, Month.DECEMBER, 15),
                        CargoType.REFRIGERATED, "1500")));

        assertThat(differences).hasSize(5);
        assertThat(differences).anySatisfy(difference -> assertThat(difference)
                .contains("出発地").contains("JPTYO").contains("JPOSA"));
        assertThat(differences).anySatisfy(difference -> assertThat(difference)
                .contains("目的地").contains("USNYC").contains("USLAX"));
        assertThat(differences).anySatisfy(difference -> assertThat(difference)
                .contains("到着期限").contains("2026-12-01").contains("2026-12-15"));
        assertThat(differences).anySatisfy(difference -> assertThat(difference)
                .contains("貨物種別").contains("GENERAL").contains("REFRIGERATED"));
        assertThat(differences).anySatisfy(difference -> assertThat(difference)
                .contains("重量").contains("1200").contains("1500"));
    }

    @Test
    @DisplayName("違う項目だけを返す（同じ項目は並べない）")
    void reportsOnlyWhatDiffers() {
        assertThat(quoted().differencesAgainst(QuotationTerms.of(
                booking("JPTYO", "USNYC", DEADLINE, CargoType.GENERAL, "1500"))))
                .singleElement()
                .satisfies(difference -> assertThat(difference).contains("重量"));
    }

    @Test
    @DisplayName("見積の側が持たない項目は「違う」と言わない（欠けを差分に見せない）")
    void ignoresTermsTheQuotationDoesNotHave() {
        // 見積が 5 項目を持たない形で保存されていることがある（列を足す前の行）。
        // **持っていない項目は比べない**——比べると「JPTYO → null」のような
        // 読めない差分が並ぶ。
        var partial = new QuotationTerms(null, null, null, null, null);

        assertThat(partial.differencesAgainst(QuotationTerms.of(
                booking("JPTYO", "USNYC", DEADLINE, CargoType.GENERAL, "1200"))))
                .isEmpty();
    }

    @Test
    @DisplayName("予約の側に経路や貨物の指定が無くても落ちない")
    void extractsNothingFromAnEmptyBooking() {
        // 予約の入力は集約が断るが、**取り出しの側で落ちると原因が隠れる**
        // ——断った理由でなく NullPointerException が上がる。
        var terms = QuotationTerms.of(new BookCargoCommand("B-1", "SHP-000001",
                null, null, "sales01"));

        assertThat(terms.originUnLocode()).isNull();
        assertThat(terms.cargoType()).isNull();
        assertThat(quoted().differencesAgainst(terms))
                .as("重量だけは相手が無ければ比べない（数として比べるので相手が要る）")
                .hasSize(4);
    }

    @Test
    @DisplayName("相手が無ければ違いも無い（断らない）")
    void reportsNothingWithoutTheOtherSide() {
        assertThat(quoted().differencesAgainst(null)).isEmpty();
    }

    @Test
    @DisplayName("予約の入力から 5 項目を取り出す（取り出し方も 1 か所）")
    void extractsTheFiveTermsFromABooking() {
        QuotationTerms terms = QuotationTerms.of(
                booking("JPTYO", "USNYC", DEADLINE, CargoType.GENERAL, "1200"));

        assertThat(terms.originUnLocode()).isEqualTo("JPTYO");
        assertThat(terms.destinationUnLocode()).isEqualTo("USNYC");
        assertThat(terms.arrivalDeadline()).isEqualTo(DEADLINE);
        assertThat(terms.cargoType()).isEqualTo("GENERAL");
        assertThat(terms.weightKg()).isEqualByComparingTo("1200");
    }
}
