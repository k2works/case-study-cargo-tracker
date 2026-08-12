package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.handling.domain.model.valueobjects.CargoBookingId;
import com.example.cargotracker.handling.domain.model.valueobjects.ClaimConfirmation;
import com.example.cargotracker.handling.domain.model.valueobjects.ClaimConfirmationMethod;
import com.example.cargotracker.handling.domain.model.valueobjects.HandledCargo;
import com.example.cargotracker.handling.domain.model.aggregates.HandlingActivity;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingDetails;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handling.domain.model.commands.RegisterHandlingCommand;
import com.example.cargotracker.handling.domain.model.valueobjects.ScannedTrackingNumber;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 引取確認（US16）。
 *
 * <p><strong>引き渡し証明は事故時の唯一の防御線である</strong>（{@code ui_design.md}）。
 * 「渡した」「受け取っていない」の争いになったとき、確認の記録が無ければ会社が負う。
 *
 * <p>要否は<strong>荷役種別自身が知る</strong>。登録処理に対応表を書き写すと、
 * 種別が増えたときに片方だけが更新される（既存のデシジョンテーブルと同じ形）。
 */
@DisplayName("引取確認（US16）")
class ClaimConfirmationTest {

    private static final java.time.ZoneId 業務のタイムゾーン = java.time.ZoneId.of("Asia/Tokyo");


    private static final Instant 作業日時 = Instant.parse("2026-09-20T01:00:00Z");

    private static RegisterHandlingCommand コマンド(
            HandlingType type, ClaimConfirmation confirmation) {
        return new RegisterHandlingCommand(
                new HandledCargo(new ScannedTrackingNumber("TRK-20260901-0001"),
                        new CargoBookingId(UUID.randomUUID())),
                HandlingDetails.of(
                        type,
                        type.requiresVoyageNumber()
                                ? new com.example.cargotracker.handling.domain.model.valueobjects
                                        .HandlingVoyageNumber("V001")
                                : null,
                        confirmation),
                作業日時, Location.of("USLAX"),
                null, "港湾太郎");
    }

    private static ClaimConfirmation 確認コード() {
        return ClaimConfirmation.byCode("123456", "受取花子");
    }

    // ---- 要否は種別が知る ----

    @Test
    void 引取には荷受人確認が必要である() {
        assertThat(HandlingType.CLAIM.requiresClaimConfirmation()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = HandlingType.class,
            names = {"RECEIVE", "LOAD", "UNLOAD", "CUSTOMS"})
    void 引取以外は荷受人確認を必要としない(HandlingType type) {
        assertThat(type.requiresClaimConfirmation()).isFalse();
    }

    // ---- 登録の可否 ----

    /** 受入基準: 荷受人確認が取得されると引取作業が記録される。 */
    @Test
    void 確認コードがあれば引取を登録できる() {
        var activity = HandlingActivity.register(
                コマンド(HandlingType.CLAIM, 確認コード()), 業務のタイムゾーン);

        assertThat(activity.claimConfirmation().code()).isEqualTo("123456");
        assertThat(activity.claimConfirmation().consigneeName()).isEqualTo("受取花子");
        assertThat(activity.claimConfirmation().method())
                .isEqualTo(ClaimConfirmationMethod.CONFIRMATION_CODE);
    }

    /**
     * <strong>確認なしの引取は登録できない。</strong> 荷役は原則として
     * 「予定と違っても記録する」が、<strong>引取だけは別である</strong>。
     * ここで記録を許すと、証明の無い引き渡しが「引き渡し済」として残り、
     * 争いになったときに会社が負う。
     */
    @Test
    void 確認のない引取は登録できない() {
        assertThatThrownBy(() -> HandlingActivity.register(
                コマンド(HandlingType.CLAIM, null), 業務のタイムゾーン))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("荷受人");
    }

    @ParameterizedTest
    @EnumSource(value = HandlingType.class, names = {"RECEIVE", "CUSTOMS"})
    void 引取以外は確認が無くても登録できる(HandlingType type) {
        assertThatCode(() -> HandlingActivity.register(コマンド(type, null), 業務のタイムゾーン))
                .doesNotThrowAnyException();
    }

    // ---- 確認そのものの不変条件 ----

    /**
     * <strong>確認コードと荷受人氏名はひと組である。</strong> 別々に持つと
     * 「コードはあるが誰が受け取ったか分からない」記録を作れる。
     */
    @Test
    void 荷受人氏名の無い確認は作れない() {
        assertThatThrownBy(() -> ClaimConfirmation.byCode("123456", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("荷受人氏名");
    }

    @Test
    void 確認コードの無い確認は作れない() {
        assertThatThrownBy(() -> ClaimConfirmation.byCode(" ", "受取花子"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("確認コード");
    }

    /**
     * <strong>予約の荷受人と違う氏名でも拒否しない。</strong>
     * 代理受領は実務で頻繁に起きる（{@code ui_design.md}）。伝えるのは警告である。
     */
    @Test
    void 予約の荷受人と違う氏名は警告になるが拒否されない() {
        var activity = HandlingActivity.register(
                コマンド(HandlingType.CLAIM, ClaimConfirmation.byCode("123456", "代理次郎")),
                業務のタイムゾーン);

        assertThat(activity.claimConfirmation().matchesConsignee("受取花子")).isFalse();
        assertThat(activity.claimConfirmation().matchesConsignee("代理次郎")).isTrue();
    }

    /**
     * <strong>予約に荷受人が登録されていないときは照合しない。</strong>
     * 「違う」と言えるのは、比べる相手があるときだけである。
     */
    @Test
    void 予約に荷受人が無ければ照合しない() {
        var confirmation = ClaimConfirmation.byCode("123456", "受取花子");

        assertThat(confirmation.matchesConsignee(null)).isTrue();
        assertThat(confirmation.matchesConsignee(" ")).isTrue();
    }
}
