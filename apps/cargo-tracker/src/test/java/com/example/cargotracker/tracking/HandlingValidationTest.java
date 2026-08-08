package com.example.cargotracker.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.tracking.handling.domain.model.CargoBookingId;
import com.example.cargotracker.tracking.handling.domain.model.CargoSnapshot;
import com.example.cargotracker.tracking.handling.domain.model.HandlingActivity;
import com.example.cargotracker.tracking.handling.domain.model.HandlingType;
import com.example.cargotracker.tracking.handling.domain.model.HandlingValidation;
import com.example.cargotracker.tracking.handling.domain.model.HandlingVoyageNumber;
import com.example.cargotracker.tracking.handling.domain.model.RegisterHandlingCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 荷役の妥当性検証（US15）。
 *
 * <p><strong>予定ルートと一致する便しか使わないテストにしない。</strong> それでは
 * 判定を外しても緑のままになる（IT5 の「判別しないテスト」の教訓）。
 * 一致する場合と外れる場合の両方を、同じ予定ルートに対して確かめる。
 */
@DisplayName("荷役の妥当性検証（US15）")
class HandlingValidationTest {

    private static final Instant 作業日時 = Instant.parse("2026-09-03T01:00:00Z");

    /**
     * 大阪 → 上海 → ロサンゼルスの予定ルート。
     *
     * <p><strong>途中に経由港を持たせる。</strong> 端点しか無い旅程だと、
     * 「旅程を見ていない実装」でも端点の照合だけで通ってしまう。
     */
    private static CargoSnapshot 予定ルート() {
        return new CargoSnapshot("11111111-1111-1111-1111-111111111111", "JPOSA", "USLAX",
                List.of(
                        new CargoSnapshot.LegSnapshot("V001", "JPOSA", "CNSHA"),
                        new CargoSnapshot.LegSnapshot("V002", "CNSHA", "USLAX")));
    }

    private static CargoSnapshot 経路未割り当て() {
        return new CargoSnapshot("22222222-2222-2222-2222-222222222222", "JPOSA", "USLAX",
                List.of());
    }

    private static HandlingActivity 荷役(HandlingType type, String unlocode, String voyage) {
        return HandlingActivity.register(new RegisterHandlingCommand(
                new CargoBookingId(UUID.randomUUID()), type, 作業日時,
                Location.of(unlocode),
                voyage == null ? null : new HandlingVoyageNumber(voyage),
                "港湾太郎"));
    }

    /** 受入基準: 作業場所が予定ルートと異なる場合、警告が表示される。 */
    @Test
    void 予定どおりの積込は警告にならない() {
        var validation = 荷役(HandlingType.LOAD, "JPOSA", "V001").isValidFor(予定ルート());

        assertThat(validation.outcome()).isEqualTo(HandlingValidation.Outcome.AS_PLANNED);
        assertThat(validation.hasMessage()).isFalse();
    }

    /** 予定ルートの 2 区間目も、そのまま予定どおりと判定する。 */
    @Test
    void 経由港からの積込も予定どおりと判定する() {
        var validation = 荷役(HandlingType.LOAD, "CNSHA", "V002").isValidFor(予定ルート());

        assertThat(validation.outcome()).isEqualTo(HandlingValidation.Outcome.AS_PLANNED);
    }

    /**
     * <strong>予定に無い港での積込は誤配である</strong>（荷役ビジネスルール 1）。
     *
     * <p>貨物は予定と違う船・違う港へ向かう。
     */
    @Test
    void 予定に無い港での積込は誤配になる() {
        var validation = 荷役(HandlingType.LOAD, "JPYOK", "V001").isValidFor(予定ルート());

        assertThat(validation.isMisrouted()).isTrue();
        assertThat(validation.message()).contains("JPYOK");
    }

    /**
     * <strong>港が合っていても、便が違えば誤配である。</strong>
     *
     * <p>場所だけを見る実装だと、この場合を取りこぼす。大阪から出る別の便に
     * 積み込まれた貨物は、予定と違う場所へ運ばれる。
     */
    @Test
    void 港が合っていても便が違えば誤配になる() {
        var validation = 荷役(HandlingType.LOAD, "JPOSA", "V999").isValidFor(予定ルート());

        assertThat(validation.isMisrouted()).isTrue();
        assertThat(validation.message()).contains("V999");
    }

    /**
     * <strong>積込港と荷降港を取り違えていないか。</strong>
     *
     * <p>V001 は大阪で積んで上海で降ろす便である。上海での「積込」は予定に無い。
     * 積込港と荷降港を同じ集合として扱う実装だと、この場合が通ってしまう。
     */
    @Test
    void 荷降港での積込は誤配になる() {
        var validation = 荷役(HandlingType.LOAD, "CNSHA", "V001").isValidFor(予定ルート());

        assertThat(validation.isMisrouted()).isTrue();
    }

    @Test
    void 予定どおりの荷降しは警告にならない() {
        var validation = 荷役(HandlingType.UNLOAD, "CNSHA", "V001").isValidFor(予定ルート());

        assertThat(validation.outcome()).isEqualTo(HandlingValidation.Outcome.AS_PLANNED);
    }

    @Test
    void 積込港での荷降しは誤配になる() {
        var validation = 荷役(HandlingType.UNLOAD, "JPOSA", "V001").isValidFor(予定ルート());

        assertThat(validation.isMisrouted()).isTrue();
    }

    /** 受領は予約の出発地と照合する。 */
    @Test
    void 出発地での受領は警告にならない() {
        var validation = 荷役(HandlingType.RECEIVE, "JPOSA", null).isValidFor(予定ルート());

        assertThat(validation.outcome()).isEqualTo(HandlingValidation.Outcome.AS_PLANNED);
    }

    /**
     * <strong>出発地以外での受領は警告に留める。</strong>
     *
     * <p>誤配にはしない。港の中の別のゲートで受け取るなど業務上あり得る差であり、
     * 輸送そのものは予定どおり進む。
     */
    @Test
    void 出発地以外での受領は警告になるが誤配ではない() {
        var validation = 荷役(HandlingType.RECEIVE, "JPYOK", null).isValidFor(予定ルート());

        assertThat(validation.outcome()).isEqualTo(HandlingValidation.Outcome.WARNING);
        assertThat(validation.isMisrouted()).isFalse();
    }

    /** 通関は場所を照合しない（貨物の位置を変えない手続きである）。 */
    @Test
    void 通関は場所を照合しない() {
        var validation = 荷役(HandlingType.CUSTOMS, "SGSIN", null).isValidFor(予定ルート());

        assertThat(validation.outcome()).isEqualTo(HandlingValidation.Outcome.AS_PLANNED);
    }

    /**
     * <strong>経路が割り当てられていない貨物の積込は誤配にしない。</strong>
     *
     * <p>比べる予定そのものが無い。予定が無いことを「予定と違う」と呼ぶと、
     * 誤配の件数が意味を失う。
     */
    @Test
    void 経路未割り当ての貨物の積込は誤配にならない() {
        var validation = 荷役(HandlingType.LOAD, "JPOSA", "V001").isValidFor(経路未割り当て());

        assertThat(validation.isMisrouted()).isFalse();
        assertThat(validation.message()).contains("経路");
    }

    /** 積込・荷降しは航海番号が必須である（デシジョンテーブル）。 */
    @ParameterizedTest
    @EnumSource(value = HandlingType.class, names = {"LOAD", "UNLOAD"})
    void 航海番号の無い積込と荷降しは登録できない(HandlingType type) {
        assertThatThrownBy(() -> 荷役(type, "JPOSA", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("航海番号");
    }

    /** 受領・引取・通関は航海番号が無くても登録できる。 */
    @ParameterizedTest
    @EnumSource(value = HandlingType.class, names = {"RECEIVE", "CLAIM", "CUSTOMS"})
    void 航海番号の要否がデシジョンテーブルと一致する(HandlingType type) {
        assertThat(荷役(type, "JPOSA", null).voyageNumber()).isNull();
    }
}
