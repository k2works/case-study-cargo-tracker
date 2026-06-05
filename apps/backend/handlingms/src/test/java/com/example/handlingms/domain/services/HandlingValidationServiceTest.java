package com.example.handlingms.domain.services;

import com.example.handlingms.domain.model.HandlingType;
import com.example.handlingms.domain.projections.CargoSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HandlingValidationService} のユニットテスト（IT5 3.2 / IT8 H2 持ち越し T1.11 DIP 回復）。
 *
 * <p>IT8 T1.11: domain 層 {@link HandlingValidationRepository} ポートをモック化することで、
 * infrastructure 層（Mapper）への依存を完全に排除し、純粋な domain ロジックのみを検証する。</p>
 */
class HandlingValidationServiceTest {

    private HandlingValidationRepository repository;
    private HandlingValidationService service;

    @BeforeEach
    void setUp() {
        repository = mock(HandlingValidationRepository.class);
        service = new HandlingValidationService(repository);
    }

    private CargoSnapshot snapshot(String origin, String destination) {
        CargoSnapshot s = new CargoSnapshot();
        s.setBookingId("B-001");
        s.setTrackingNumber("TRK-AB12CD3456");
        s.setOriginUnlocode(origin);
        s.setDestinationUnlocode(destination);
        s.setCargoType("GENERAL");
        return s;
    }

    // --- hasDuplicate ---

    @Test
    @DisplayName("hasDuplicate: ±5 分の window で countDuplicates を問い合わせる")
    void window5分でクエリする() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        when(repository.countDuplicates(any(), any(), any(), any(), any())).thenReturn(0L);

        service.hasDuplicate("TRK-AB12CD3456", HandlingType.RECEIVE, "JPTYO", occurredAt);

        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).countDuplicates(
                eq("TRK-AB12CD3456"), eq(HandlingType.RECEIVE), eq("JPTYO"),
                startCap.capture(), endCap.capture());
        assertThat(startCap.getValue()).isEqualTo(occurredAt.minusMinutes(5));
        assertThat(endCap.getValue()).isEqualTo(occurredAt.plusMinutes(5));
    }

    @Test
    @DisplayName("hasDuplicate: count > 0 で true")
    void 重複ありでtrue() {
        when(repository.countDuplicates(any(), any(), any(), any(), any())).thenReturn(1L);
        assertThat(service.hasDuplicate("TRK-X", HandlingType.LOAD, "JPTYO",
                LocalDateTime.of(2026, 7, 20, 10, 0))).isTrue();
    }

    @Test
    @DisplayName("hasDuplicate: count = 0 で false")
    void 重複なしでfalse() {
        when(repository.countDuplicates(any(), any(), any(), any(), any())).thenReturn(0L);
        assertThat(service.hasDuplicate("TRK-X", HandlingType.LOAD, "JPTYO",
                LocalDateTime.of(2026, 7, 20, 10, 0))).isFalse();
    }

    // --- detectUnexpected ---

    @Test
    @DisplayName("detectUnexpected: RECEIVE が origin と一致するなら empty（予定通り）")
    void RECEIVE_originと一致でempty() {
        when(repository.findCargoSnapshotByTrackingNumber("TRK-X")).thenReturn(snapshot("JPTYO", "USNYC"));

        assertThat(service.detectUnexpected("TRK-X", HandlingType.RECEIVE, "JPTYO"))
                .isEmpty();
    }

    @Test
    @DisplayName("detectUnexpected: RECEIVE が origin と異なるなら理由を返す")
    void RECEIVE_origin不一致で理由() {
        when(repository.findCargoSnapshotByTrackingNumber("TRK-X")).thenReturn(snapshot("JPTYO", "USNYC"));

        Optional<String> reason = service.detectUnexpected("TRK-X", HandlingType.RECEIVE, "CNHKG");

        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("RECEIVE").contains("JPTYO").contains("CNHKG");
    }

    @Test
    @DisplayName("detectUnexpected: CLAIM が destination と一致するなら empty")
    void CLAIM_destinationと一致でempty() {
        when(repository.findCargoSnapshotByTrackingNumber("TRK-X")).thenReturn(snapshot("JPTYO", "USNYC"));

        assertThat(service.detectUnexpected("TRK-X", HandlingType.CLAIM, "USNYC"))
                .isEmpty();
    }

    @Test
    @DisplayName("detectUnexpected: CLAIM が destination と異なるなら理由を返す")
    void CLAIM_destination不一致で理由() {
        when(repository.findCargoSnapshotByTrackingNumber("TRK-X")).thenReturn(snapshot("JPTYO", "USNYC"));

        Optional<String> reason = service.detectUnexpected("TRK-X", HandlingType.CLAIM, "CNHKG");

        assertThat(reason).isPresent();
        assertThat(reason.get()).contains("CLAIM").contains("USNYC").contains("CNHKG");
    }

    @Test
    @DisplayName("detectUnexpected: LOAD / UNLOAD / CUSTOMS は本 IT では判定保留（empty）")
    void LOAD_UNLOAD_CUSTOMSは保留() {
        when(repository.findCargoSnapshotByTrackingNumber("TRK-X")).thenReturn(snapshot("JPTYO", "USNYC"));

        assertThat(service.detectUnexpected("TRK-X", HandlingType.LOAD, "ANYWHERE")).isEmpty();
        assertThat(service.detectUnexpected("TRK-X", HandlingType.UNLOAD, "ANYWHERE")).isEmpty();
        assertThat(service.detectUnexpected("TRK-X", HandlingType.CUSTOMS, "ANYWHERE")).isEmpty();
    }

    @Test
    @DisplayName("detectUnexpected: snapshot 未到着なら判定保留（empty）")
    void snapshot未到着で保留() {
        when(repository.findCargoSnapshotByTrackingNumber("TRK-NEW")).thenReturn(null);

        assertThat(service.detectUnexpected("TRK-NEW", HandlingType.RECEIVE, "JPTYO")).isEmpty();
    }
}
