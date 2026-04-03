package com.example.cargotracker.tracking.application.internal.outboundservices;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 例外情報照会ポート — tracking BC が exception BC の例外情報を取得するための ACL ポート。
 */
public interface ExceptionInfoQueryPort {

    List<ExceptionInfo> findByTrackingNumber(String trackingNumber);

    record ExceptionInfo(
            LocalDateTime occurredAt,
            String locationCode,
            String exceptionType,
            String displayName,
            String reason,
            String resolution
    ) {}
}
