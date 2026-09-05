package com.example.cargotracker.routing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 航海の検索条件（US07）。
 *
 * <p>「空文字は指定なし」の扱いをここ 1 か所で決める。画面とクエリハンドラの両方で
 * 判断すると、片方だけが空文字をそのまま渡し、どの航海にも一致せず一覧が黙って空になる
 * （IT3 で貨物種別の絞り込みに実際に起きた形）。</p>
 */
class VoyageSearchCriteriaTest {

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-30T00:00:00Z");

    @Test
    @DisplayName("空文字と空白は「絞り込まない」に寄せる")
    void treatsBlankAsUnspecified() {
        VoyageSearchCriteria criteria = VoyageSearchCriteria.of("  ", "", null, null, " ");

        assertThat(criteria.departure()).isNull();
        assertThat(criteria.arrival()).isNull();
        assertThat(criteria.cargoType()).isNull();
        assertThat(criteria.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("港は UN/LOCODE として受け取る")
    void validatesPorts() {
        VoyageSearchCriteria criteria = VoyageSearchCriteria.of("JPTYO", "USNYC", null, null, null);

        assertThat(criteria.departure()).isEqualTo("JPTYO");
        assertThat(criteria.arrival()).isEqualTo("USNYC");

        // 小文字を通すと、同じ港が 2 通りの書き方で入り、一致しないまま 0 件になる。
        assertThatThrownBy(() -> VoyageSearchCriteria.of("jptyo", null, null, null, null))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("知らない貨物種別は断る（0 件にしない）")
    void rejectsUnknownCargoType() {
        // 0 件は「その条件の航海が無い」と読める。入力の誤りは断って気づかせる。
        assertThatThrownBy(() -> VoyageSearchCriteria.of(null, null, null, null, "UNKNOWN"))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("知らない貨物種別");
    }

    @Test
    @DisplayName("出発期間は開始が終了より後にならない")
    void rejectsInvertedPeriod() {
        assertThatThrownBy(() -> VoyageSearchCriteria.of(null, null, TO, FROM, null))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("出発期間");
    }

    @Test
    @DisplayName("開始と終了が同じ日時は許す")
    void allowsSameInstantPeriod() {
        assertThat(VoyageSearchCriteria.of(null, null, FROM, FROM, null).departFrom())
                .isEqualTo(FROM);
    }

    @Test
    @DisplayName("片側だけの期間も条件になる")
    void allowsOpenEndedPeriod() {
        assertThat(VoyageSearchCriteria.of(null, null, FROM, null, null).isEmpty()).isFalse();
        assertThat(VoyageSearchCriteria.of(null, null, null, TO, null).isEmpty()).isFalse();
    }

    @Test
    @DisplayName("貨物種別は列挙の名前で持つ")
    void keepsCargoTypeName() {
        assertThat(VoyageSearchCriteria.of(null, null, null, null, "HAZARDOUS").cargoType())
                .isEqualTo(CargoType.HAZARDOUS.name());
    }
}
