package com.example.cargotracker.handlingms.domain.model.valueobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * handlingms の値オブジェクトに対するユニットテスト。
 *
 * <p>不変条件・等価性・境界値を網羅する（ドメイン層 PIT 75% / 行 90% の主指標）。</p>
 */
@DisplayName("handlingms 値オブジェクト")
class ValueObjectsTest {

    // ===== UnLocode =====

    @Test
    @DisplayName("UnLocode: 5 文字の英数字なら受け入れる")
    void unLocode_正常() {
        assertThat(new UnLocode("JPTYO").value()).isEqualTo("JPTYO");
        assertThat(new UnLocode("USNYC").value()).isEqualTo("USNYC");
    }

    @Test
    @DisplayName("UnLocode: 形式不正の入力を拒否する")
    void unLocode_異常() {
        assertThatThrownBy(() -> new UnLocode("jp")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnLocode("jptyo")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnLocode("JPTY")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnLocode("123AB")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnLocode(null)).isInstanceOf(NullPointerException.class);
    }

    // ===== Location =====

    @Test
    @DisplayName("Location.of: UN/LOCODE 文字列から生成できる")
    void location_of() {
        Location loc = Location.of("JPTYO");
        assertThat(loc.unLocode().value()).isEqualTo("JPTYO");
        assertThat(loc.portName()).isNull();
    }

    @Test
    @DisplayName("Location: unLocode が null だと NullPointerException")
    void location_null拒否() {
        assertThatThrownBy(() -> new Location(null, "Tokyo"))
                .isInstanceOf(NullPointerException.class);
    }

    // ===== HandlerId =====

    @Test
    @DisplayName("HandlerId: 空文字または null を拒否する")
    void handlerId_異常() {
        assertThatThrownBy(() -> new HandlerId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HandlerId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HandlerId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("HandlerId: 通常文字列を受け入れる")
    void handlerId_正常() {
        assertThat(new HandlerId("handler-001").value()).isEqualTo("handler-001");
    }

    // ===== VoyageNumber =====

    @Test
    @DisplayName("VoyageNumber: 通常文字列を受け入れる")
    void voyageNumber_正常() {
        assertThat(new VoyageNumber("V-MOL-001").value()).isEqualTo("V-MOL-001");
    }

    @Test
    @DisplayName("VoyageNumber: 空文字・null・20 文字超を拒否する")
    void voyageNumber_異常() {
        assertThatThrownBy(() -> new VoyageNumber("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoyageNumber(" ")).isInstanceOf(IllegalArgumentException.class);
        final String tooLong = "X".repeat(21);
        assertThatThrownBy(() -> new VoyageNumber(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VoyageNumber(null)).isInstanceOf(NullPointerException.class);
    }

    // ===== TrackingNumber =====

    @Test
    @DisplayName("TrackingNumber: 'TRK-' で始まる文字列を受け入れる")
    void trackingNumber_正常() {
        assertThat(new TrackingNumber("TRK-20260720-ABC12345").value()).isEqualTo("TRK-20260720-ABC12345");
    }

    @Test
    @DisplayName("TrackingNumber: 'TRK-' 始まり以外・空文字・25 文字超を拒否する")
    void trackingNumber_異常() {
        assertThatThrownBy(() -> new TrackingNumber("ABC-20260720-XYZ"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrackingNumber("")).isInstanceOf(IllegalArgumentException.class);
        final String tooLong = "TRK-" + "X".repeat(30);
        assertThatThrownBy(() -> new TrackingNumber(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrackingNumber(null)).isInstanceOf(NullPointerException.class);
    }

    // ===== HandlingType =====

    @Test
    @DisplayName("HandlingType: LOAD/UNLOAD は voyageNumber が必須")
    void handlingType_voyage必須() {
        assertThat(HandlingType.LOAD.requiresVoyageNumber()).isTrue();
        assertThat(HandlingType.UNLOAD.requiresVoyageNumber()).isTrue();
        assertThat(HandlingType.RECEIVE.requiresVoyageNumber()).isFalse();
        assertThat(HandlingType.CLAIM.requiresVoyageNumber()).isFalse();
        assertThat(HandlingType.CUSTOMS.requiresVoyageNumber()).isFalse();
    }

    @Test
    @DisplayName("HandlingType: CLAIM のみ ClaimVerification が必須")
    void handlingType_claim必須() {
        assertThat(HandlingType.CLAIM.requiresClaimVerification()).isTrue();
        assertThat(HandlingType.RECEIVE.requiresClaimVerification()).isFalse();
        assertThat(HandlingType.LOAD.requiresClaimVerification()).isFalse();
    }

    // ===== ClaimVerification =====

    @Test
    @DisplayName("ClaimVerification: 確認コードのみで生成可")
    void claimVerification_確認コードのみ() {
        var cv = new ClaimVerification("John Doe", null, "AX9-2K7",
                LocalDateTime.of(2026, 8, 10, 14, 30));
        assertThat(cv.consigneeName()).isEqualTo("John Doe");
        assertThat(cv.confirmationCode()).isEqualTo("AX9-2K7");
        assertThat(cv.signatureRef()).isNull();
    }

    @Test
    @DisplayName("ClaimVerification: 署名 ref のみで生成可")
    void claimVerification_署名のみ() {
        var cv = new ClaimVerification("Jane Smith", "s3://x.png", null,
                LocalDateTime.of(2026, 8, 10, 14, 30));
        assertThat(cv.signatureRef()).isEqualTo("s3://x.png");
        assertThat(cv.confirmationCode()).isNull();
    }

    @Test
    @DisplayName("ClaimVerification: signatureRef も confirmationCode も無いと拒否")
    void claimVerification_両方なしで拒否() {
        LocalDateTime verifiedAt = LocalDateTime.now();
        assertThatThrownBy(() -> new ClaimVerification("John Doe", null, null, verifiedAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("いずれかが必須");
    }

    @Test
    @DisplayName("ClaimVerification: 必須項目 null は拒否")
    void claimVerification_null拒否() {
        LocalDateTime verifiedAt = LocalDateTime.now();
        assertThatThrownBy(() -> new ClaimVerification(null, null, "X", verifiedAt))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClaimVerification(" ", null, "X", verifiedAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClaimVerification("John", null, "X", null))
                .isInstanceOf(NullPointerException.class);
    }

    // ===== CargoSnapshot =====

    @Test
    @DisplayName("CargoSnapshot.isExpectedHandling: RECEIVE は出発地で True")
    void cargoSnapshot_receive() {
        var snap = new CargoSnapshot(
                "B-001",
                new TrackingNumber("TRK-20260720-AAAA1111"),
                Location.of("JPTYO"),
                Location.of("DEHAM"),
                "GENERAL");
        assertThat(snap.isExpectedHandling(HandlingType.RECEIVE, Location.of("JPTYO"))).isTrue();
        assertThat(snap.isExpectedHandling(HandlingType.RECEIVE, Location.of("SGSIN"))).isFalse();
    }

    @Test
    @DisplayName("CargoSnapshot.isExpectedHandling: CLAIM/CUSTOMS は到着地で True")
    void cargoSnapshot_claim_customs() {
        var snap = new CargoSnapshot(
                "B-001",
                new TrackingNumber("TRK-20260720-AAAA1111"),
                Location.of("JPTYO"),
                Location.of("DEHAM"),
                "GENERAL");
        assertThat(snap.isExpectedHandling(HandlingType.CLAIM, Location.of("DEHAM"))).isTrue();
        assertThat(snap.isExpectedHandling(HandlingType.CLAIM, Location.of("JPTYO"))).isFalse();
        assertThat(snap.isExpectedHandling(HandlingType.CUSTOMS, Location.of("DEHAM"))).isTrue();
    }

    @Test
    @DisplayName("CargoSnapshot.isExpectedHandling: LOAD/UNLOAD は IT5 では常に True")
    void cargoSnapshot_load_unload() {
        var snap = new CargoSnapshot(
                "B-001",
                new TrackingNumber("TRK-20260720-AAAA1111"),
                Location.of("JPTYO"),
                Location.of("DEHAM"),
                "GENERAL");
        assertThat(snap.isExpectedHandling(HandlingType.LOAD, Location.of("SGSIN"))).isTrue();
        assertThat(snap.isExpectedHandling(HandlingType.UNLOAD, Location.of("SGSIN"))).isTrue();
    }

    @Test
    @DisplayName("CargoSnapshot: 必須項目 null は拒否")
    void cargoSnapshot_null拒否() {
        TrackingNumber trk = new TrackingNumber("TRK-20260720-AAAA1111");
        Location tyo = Location.of("JPTYO");
        Location ham = Location.of("DEHAM");
        assertThatThrownBy(() -> new CargoSnapshot(null, trk, tyo, ham, "GENERAL"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CargoSnapshot("B-001", trk, null, ham, "GENERAL"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CargoSnapshot("B-001", trk, tyo, null, "GENERAL"))
                .isInstanceOf(NullPointerException.class);
    }
}
