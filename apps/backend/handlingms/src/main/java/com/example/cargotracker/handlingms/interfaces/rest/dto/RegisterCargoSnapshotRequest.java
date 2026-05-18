package com.example.cargotracker.handlingms.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * CargoSnapshot 登録リクエスト DTO（IT5 暫定）。
 *
 * <p>本来は bookingms の {@code CargoBookedEvent} を購読して自動維持する設計だが、
 * IT5 では event 直接購読を実装しないため REST API ベースの ACL として提供する。
 * IT6 以降で Axon Event Bus 経由のサブスクリプションに置き換える予定。</p>
 *
 * <p>関連: ADR-0012 handlingms と trackingms の責務分離</p>
 */
public record RegisterCargoSnapshotRequest(
        @NotBlank String bookingId,
        String trackingNumber,
        @NotBlank String originUnlocode,
        @NotBlank String destinationUnlocode,
        @NotBlank String cargoType,
        @NotNull LocalDate arrivalDeadline,
        @NotBlank String bookingStatus) {
}
