package com.example.cargotracker.shared.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 追跡番号発行イベント（shared kernel）。
 *
 * <p>bookingms の Cargo aggregate が IssueTrackingNumberCommand を処理して発行する。
 * bookingms 内の CargoProjectionsEventHandler が cargo_summary を更新し、
 * CargoTrackedEvent とともに trackingms ACL に転送される。</p>
 */
public record TrackingNumberIssuedEvent(@EventTag String bookingId, String trackingNumber) {
}
