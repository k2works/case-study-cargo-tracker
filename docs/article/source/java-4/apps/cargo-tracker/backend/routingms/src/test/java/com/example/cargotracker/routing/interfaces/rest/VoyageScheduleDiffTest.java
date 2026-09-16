package com.example.cargotracker.routing.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.interfaces.rest.VoyageScheduleDiff.FieldChange;
import com.example.cargotracker.routing.interfaces.rest.VoyageScheduleDiff.VoyageSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 更新前後の差分（US25 §受入基準 2）。
 *
 * <p>差分はサーバが出す。画面で 2 つの値を並べて {@code if} を積み上げると、航海に属性が
 * 増えるたびに比べ忘れが生まれる（IT3 の投影で実際に起きた形）。</p>
 */
class VoyageScheduleDiffTest {

    private static final Instant DEPART = Instant.parse("2026-09-10T09:00:00Z");
    private static final Instant ARRIVE = Instant.parse("2026-09-24T18:00:00Z");

    private static VoyageSnapshot snapshot() {
        return new VoyageSnapshot("MOL", "商船三井", "MOL EXPRESS", List.of("GENERAL"),
                List.of(new VoyageSnapshot.Movement("JPTYO", "USNYC", DEPART, ARRIVE)));
    }

    @Test
    @DisplayName("変わっていなければ差分は空")
    void hasNoChangeWhenIdentical() {
        assertThat(VoyageScheduleDiff.between(snapshot(), snapshot())).isEmpty();
    }

    @Test
    @DisplayName("変わった項目だけが並ぶ")
    void listsChangedFields() {
        VoyageSnapshot after = new VoyageSnapshot("MOL", "商船三井", "MOL VOYAGER",
                List.of("GENERAL"),
                List.of(new VoyageSnapshot.Movement("JPTYO", "USNYC", DEPART, ARRIVE)));

        assertThat(VoyageScheduleDiff.between(snapshot(), after))
                .extracting(FieldChange::label, FieldChange::before, FieldChange::after)
                .containsExactly(tuple3("船名", "MOL EXPRESS", "MOL VOYAGER"));
    }

    private static org.assertj.core.groups.Tuple tuple3(String a, String b, String c) {
        return org.assertj.core.api.Assertions.tuple(a, b, c);
    }

    @Test
    @DisplayName("寄港地の入れ替えも差分に出る")
    void detectsMovementChanges() {
        VoyageSnapshot after = new VoyageSnapshot("MOL", "商船三井", "MOL EXPRESS",
                List.of("GENERAL"),
                List.of(new VoyageSnapshot.Movement("JPTYO", "SGSIN", DEPART, ARRIVE)));

        assertThat(VoyageScheduleDiff.between(snapshot(), after))
                .extracting(FieldChange::label)
                .containsExactly("寄港地");
    }

    @Test
    @DisplayName("受入貨物種別の増減も差分に出る")
    void detectsAcceptedCargoTypeChanges() {
        VoyageSnapshot after = new VoyageSnapshot("MOL", "商船三井", "MOL EXPRESS",
                List.of("GENERAL", "HAZARDOUS"),
                List.of(new VoyageSnapshot.Movement("JPTYO", "USNYC", DEPART, ARRIVE)));

        assertThat(VoyageScheduleDiff.between(snapshot(), after))
                .extracting(FieldChange::label)
                .containsExactly("対応貨物種別");
    }

    @Test
    @DisplayName("比べる項目は集約が持つ内容の全部で、すべてに名前がある")
    void comparesEveryPartOfTheSnapshot() {
        // 項目ごとの if を積み上げると、属性が増えたときに比べ忘れる。差分は
        // レコード要素そのものを回すので、要素を足したら名前も要る。足さずに
        // 済ませられると、その項目は差分に出ないまま黙って更新される。
        assertThat(VoyageScheduleDiff.comparedFields()).hasSize(5);
        VoyageScheduleDiff.comparedFields()
                .forEach(field -> assertThat(VoyageScheduleDiff.labelOf(field)).isNotBlank());
    }

    @Test
    @DisplayName("名簿に無い項目は素通りさせない")
    void rejectsUnlistedField() {
        // 「載っていないものを通す」名簿は、載せ忘れたものほど漏れる。
        assertThatThrownBy(() -> VoyageScheduleDiff.labelOf("unknownField"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknownField");
    }
}
