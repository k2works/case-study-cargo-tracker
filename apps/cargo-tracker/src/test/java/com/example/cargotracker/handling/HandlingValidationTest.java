package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.handling.domain.model.CargoBookingId;
import com.example.cargotracker.handling.domain.model.CargoSnapshot;
import com.example.cargotracker.handling.domain.model.ClaimConfirmation;
import com.example.cargotracker.handling.domain.model.HandledCargo;
import com.example.cargotracker.handling.domain.model.HandlingActivity;
import com.example.cargotracker.handling.domain.model.HandlingDetails;
import com.example.cargotracker.handling.domain.model.HandlingType;
import com.example.cargotracker.handling.domain.model.HandlingValidation;
import com.example.cargotracker.handling.domain.model.HandlingVoyageNumber;
import com.example.cargotracker.handling.domain.model.RegisterHandlingCommand;
import com.example.cargotracker.handling.domain.model.ScannedTrackingNumber;
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

    private static final java.time.ZoneId 業務のタイムゾーン = java.time.ZoneId.of("Asia/Tokyo");


    private static final Instant 作業日時 = Instant.parse("2026-09-03T01:00:00Z");

    /**
     * 大阪 → 上海 → ロサンゼルスの予定ルート。
     *
     * <p><strong>途中に経由港を持たせる。</strong> 端点しか無い旅程だと、
     * 「旅程を見ていない実装」でも端点の照合だけで通ってしまう。
     */
    private static CargoSnapshot 予定ルート() {
        return 予定ルート("受取花子");
    }

    private static CargoSnapshot 予定ルート(String consigneeName) {
        return new CargoSnapshot("11111111-1111-1111-1111-111111111111", "JPOSA", "USLAX",
                consigneeName,
                List.of(
                        new CargoSnapshot.LegSnapshot("V001", "JPOSA", "CNSHA"),
                        new CargoSnapshot.LegSnapshot("V002", "CNSHA", "USLAX")));
    }

    private static CargoSnapshot 経路未割り当て() {
        return new CargoSnapshot("22222222-2222-2222-2222-222222222222", "JPOSA", "USLAX",
                "受取花子", List.of());
    }

    private static HandlingActivity 引取(String unlocode, String consigneeName) {
        return HandlingActivity.register(new RegisterHandlingCommand(
                new HandledCargo(new ScannedTrackingNumber("TRK-20260903-0001"),
                        new CargoBookingId(UUID.randomUUID())),
                HandlingDetails.claim(ClaimConfirmation.byCode("123456", consigneeName)),
                作業日時, Location.of(unlocode), null, "港湾太郎"), 業務のタイムゾーン);
    }

    private static HandlingActivity 荷役(HandlingType type, String unlocode, String voyage) {
        return HandlingActivity.register(new RegisterHandlingCommand(
                new HandledCargo(new ScannedTrackingNumber("TRK-20260903-0001"),
                        new CargoBookingId(UUID.randomUUID())),
                // **要否は種別自身が知る。** ここに「CLAIM のとき」と書き写すと、
                // 種別が増えたときに片方だけが更新される（本番と同じ判断を使う）
                HandlingDetails.of(
                        type,
                        voyage == null ? null : new HandlingVoyageNumber(voyage),
                        type.requiresClaimConfirmation()
                                ? ClaimConfirmation.byCode("123456", "受取花子") : null),
                作業日時,
                Location.of(unlocode),
                null, "港湾太郎"), 業務のタイムゾーン);
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

    // ---- 荷受人の照合（US16。IT7 レビュー H4）----

    /**
     * <strong>予約の荷受人と違う人が受け取ったら警告する。</strong>
     *
     * <p>画面のコメントは「予約の荷受人と違えば警告を出し、メモへの理由記入を求める」と
     * 宣言していたが、<strong>照合は場所しか見ておらず氏名は見ていなかった</strong>
     * （IT7 レビュー H4）。{@code ClaimConfirmation.matchesConsignee} は定義済みで
     * テストからしか呼ばれていない状態であり、<strong>配線漏れの典型</strong>だった。
     *
     * <p><strong>拒否はしない。</strong> 代理受領は実務で頻繁に起きる。
     */
    @Test
    void 予約の荷受人と違う人の引取は警告になる() {
        var validation = 引取("USLAX", "代理次郎").isValidFor(予定ルート("受取花子"));

        assertThat(validation.hasMessage()).isTrue();
        assertThat(validation.message()).contains("受取花子").contains("代理次郎");
        // **誤配ではない。** 貨物は正しい港に着いており、輸送は予定どおり進んだ
        assertThat(validation.isMisrouted()).isFalse();
    }

    @Test
    void 予約の荷受人と同じ人の引取は警告にならない() {
        var validation = 引取("USLAX", "受取花子").isValidFor(予定ルート("受取花子"));

        assertThat(validation.hasMessage()).isFalse();
    }

    /**
     * <strong>予約に荷受人が登録されていなければ照合しない。</strong>
     * 「違う」と言えるのは比べる相手があるときだけである。無いことを不一致と呼ぶと、
     * 荷受人未登録の予約すべてに警告が出て、警告そのものが読まれなくなる。
     */
    @Test
    void 予約に荷受人が無ければ引取は警告にならない() {
        var validation = 引取("USLAX", "受取花子").isValidFor(予定ルート(null));

        assertThat(validation.hasMessage()).isFalse();
    }

    /**
     * <strong>場所の違いと氏名の違いは両方伝える。</strong> どちらか一方しか
     * 出さないと、作業員はもう片方に気づかない。
     */
    @Test
    void 場所も氏名も違う引取は両方を伝える() {
        var validation = 引取("JPOSA", "代理次郎").isValidFor(予定ルート("受取花子"));

        assertThat(validation.message()).contains("目的地").contains("代理次郎");
        assertThat(validation.isMisrouted()).isFalse();
    }

    // ---- 作業日時の範囲（IT6 レビュー M8。IT7 計画タスク 1-4）----

    private static HandlingActivity 受領(String at) {
        return HandlingActivity.register(new RegisterHandlingCommand(
                new HandledCargo(new ScannedTrackingNumber("TRK-20260903-0001"),
                        new CargoBookingId(UUID.randomUUID())),
                HandlingDetails.receive(), Instant.parse(at),
                Location.of("JPOSA"), null, "港湾太郎"), 業務のタイムゾーン);
    }

    /**
     * <strong>追跡番号の発行日より前の作業は登録できない。</strong>
     *
     * <p>追跡番号は {@code TRK-YYYYMMDD-NNNN} であり、日付の部分が発行日である。
     * <strong>発行前に荷役は起こりえない</strong>（貨物はまだ追跡の対象ですらない）。
     * 打ち間違いで年を 1 つ戻すのは現場で起きるが、それを受け入れると
     * <strong>履歴の並びが壊れ、現在地が読めなくなる</strong>。
     */
    @Test
    void 発行日より前の作業日時は登録できない() {
        assertThatThrownBy(() -> 受領("2026-09-02T01:00:00Z"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("追跡番号の発行日");
    }

    /** 発行日当日は登録できる（発行したその日に受領するのは普通である）。 */
    @Test
    void 発行日当日の作業日時は登録できる() {
        assertThatCode(() -> 受領("2026-09-03T00:30:00Z")).doesNotThrowAnyException();
    }

    /**
     * <strong>未来日時は拒否しない。</strong> {@code ui_design.md} は
     * 「投機的な登録は許可」と定めている（積込の予定を先に入れる運用がある）。
     * <strong>拒否と警告を取り違えない。</strong>
     */
    @Test
    void 未来の作業日時は登録できる() {
        assertThatCode(() -> 受領("2099-01-01T00:00:00Z")).doesNotThrowAnyException();
    }
}
