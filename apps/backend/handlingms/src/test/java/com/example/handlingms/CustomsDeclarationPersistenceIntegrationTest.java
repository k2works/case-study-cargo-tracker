package com.example.handlingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.handlingms.application.port.CustomsDeclarationRepository;
import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.CustomsDeclaration;
import com.example.handlingms.domain.model.CustomsStatus;
import com.example.handlingms.domain.model.DeclarationNumber;
import com.example.handlingms.domain.model.HandlingTrackingNumber;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 通関申告が実際の DB で成立することを確認する（US29）。
 *
 * <p><strong>保存して読み直してから検証する。</strong>手元の集約を見ても、行に
 * 残っていないことに気づけない（[ADR-024] 決定 2 と同じ立場）。特に履歴は
 * 「積んだつもり」で落ちやすい。
 *
 * <p>土台（{@link HandlingIntegrationTestBase}）を継承する。テストごとに Postgres を
 * 立てると、資源が足りずに<strong>関係のないテストが落ちる</strong>（IT7 で 4 回踏んだ）。
 */
@DisplayName("通関申告の永続化")
class CustomsDeclarationPersistenceIntegrationTest extends HandlingIntegrationTestBase {

    private static final Instant DECLARED_AT = Instant.parse("2027-09-02T00:00:00Z");

    @Autowired
    private CustomsDeclarationRepository declarations;

    private CustomsDeclaration declare(String number, String trackingNumber) {
        return declarations.save(CustomsDeclaration.declare(
                DeclarationNumber.of(number), CargoBookingId.of("BKG-2026000001"),
                HandlingTrackingNumber.of(trackingNumber), DECLARED_AT, "初回申告", "handler01"));
    }

    @Test
    @DisplayName("申告した内容が、読み直しても全項目そろっている")
    void keepsEveryFieldAcrossAReload() {
        CustomsDeclaration saved = declare("DEC-P0001", "TRK-20260823-1001");

        CustomsDeclaration reloaded = declarations.findById(saved.id()).orElseThrow();

        // 項目ごとに比べる形にすると、属性が増えたときに比較を足し忘れる（IT6 の欠陥 5）
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(saved);
    }

    /** 登録そのものも履歴の 1 行目として残る。**何も無い状態からは始まらない**。 */
    @Test
    @DisplayName("登録が履歴の 1 行目として行に残る")
    void persistsTheDeclarationItselfAsHistory() {
        CustomsDeclaration saved = declare("DEC-P0002", "TRK-20260823-1002");

        CustomsDeclaration reloaded = declarations.findById(saved.id()).orElseThrow();

        assertThat(reloaded.history()).hasSize(1);
        assertThat(reloaded.history().getFirst().reason()).isEqualTo("申告を登録しました");
        assertThat(reloaded.history().getFirst().changedBy()).isEqualTo("handler01");
    }

    /** US29-8。**状態と履歴は同じ呼び出しで書く。** */
    @Test
    @DisplayName("状態を更新すると、履歴が行に積まれる")
    void appendsHistoryWhenStatusChanges() {
        CustomsDeclaration saved = declare("DEC-P0003", "TRK-20260823-1003");

        declarations.updateStatus(saved.updateStatus(
                CustomsStatus.HELD, "tracker01", "書類不備", Instant.parse("2027-09-03T00:00:00Z")));

        CustomsDeclaration reloaded = declarations.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(CustomsStatus.HELD);
        assertThat(reloaded.history()).hasSize(2);
        assertThat(reloaded.history().getLast().reason()).isEqualTo("書類不備");
    }

    /**
     * <strong>更新で行が増えない。</strong>「常に INSERT する save」で更新まで賄うと、
     * 最初の更新のときに行が増える。作成しか起きないうちは表面化しない。
     */
    @Test
    @DisplayName("更新しても、申告の行は増えない")
    void doesNotInsertARowOnUpdate() {
        CustomsDeclaration saved = declare("DEC-P0004", "TRK-20260823-1004");

        declarations.updateStatus(saved.updateStatus(
                CustomsStatus.CLEARED, "tracker01", "通関完了",
                Instant.parse("2027-09-03T00:00:00Z")));

        List<CustomsDeclaration> found =
                declarations.search(null, "TRK-20260823-1004", null, 100);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().status()).isEqualTo(CustomsStatus.CLEARED);
    }

    /** [ADR-025] 決定 7。**決着していない申告を引ける**。登録側がこれで 2 通目を断る。 */
    @Test
    @DisplayName("決着していない申告だけを引ける")
    void findsOnlyUnsettledDeclarations() {
        CustomsDeclaration pending = declare("DEC-P0005", "TRK-20260823-1005");

        assertThat(declarations
                .findUnsettledByTrackingNumber(HandlingTrackingNumber.of("TRK-20260823-1005")))
                .isPresent();

        declarations.updateStatus(pending.updateStatus(
                CustomsStatus.REJECTED, "tracker01", "不備", Instant.parse("2027-09-03T00:00:00Z")));

        assertThat(declarations
                .findUnsettledByTrackingNumber(HandlingTrackingNumber.of("TRK-20260823-1005")))
                .as("不可になった申告が、まだ決着していない扱いになっている")
                .isEmpty();
    }

    /** US29-7。3 条件で絞れる。**絞り込みは SQL で行う**。 */
    @Test
    @DisplayName("追跡番号と状態で絞り込める")
    void searchesByTrackingNumberAndStatus() {
        declare("DEC-P0006", "TRK-20260823-1006");
        CustomsDeclaration other = declare("DEC-P0007", "TRK-20260823-1007");
        declarations.updateStatus(other.updateStatus(
                CustomsStatus.HELD, "tracker01", "書類不備", Instant.parse("2027-09-03T00:00:00Z")));

        assertThat(declarations.search(null, "TRK-20260823-1006", null, 100))
                .extracting(declaration -> declaration.declarationNumber().value())
                .containsExactly("DEC-P0006");
        assertThat(declarations.search(null, null, CustomsStatus.HELD, 100))
                .extracting(declaration -> declaration.declarationNumber().value())
                .contains("DEC-P0007")
                .doesNotContain("DEC-P0006");
    }
}
