package com.example.trackingms.interfaces.rest;

/**
 * 追跡管理の入力（US17・US19・US20）。
 *
 * <p><strong>値の変換もメソッド本体で行う</strong>（[ADR-016]）。日時を {@code Instant} で
 * 受け取ると、Spring は認可より先に変換を試みる。
 */
public final class TrackingManagementRequests {

    private TrackingManagementRequests() {
    }

    /** 状態を手で更新する（US17-2）。 */
    public record ManualUpdateRequest(String trackingNumber, String status,
            String locationUnLocode, String occurredAt) {
    }

    /** 例外を起票する（US19-1・US20-1）。 */
    public record RaiseExceptionRequest(String trackingNumber, String exceptionType,
            String description) {
    }

    /** 例外を解決する（US19-4）。 */
    public record ResolveExceptionRequest(String trackingNumber, Long exceptionId,
            String resolutionNotes, String newEstimatedArrival) {
    }
}
