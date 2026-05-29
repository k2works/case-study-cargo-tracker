package com.example.trackingms.interfaces.rest.dto;

import java.time.LocalDateTime;

/**
 * 貨物状態手動更新リクエスト（US17 / IT5 2.3）。
 *
 * <p>{@code toStatus} は {@code TransportStatus} の列挙名。
 * {@code occurredAt} は ISO-8601 のローカル日時。{@code unlocode} は任意の港湾コード。</p>
 */
public record UpdateTransportStatusRequest(
        String toStatus,
        String unlocode,
        String voyageNumber,
        LocalDateTime occurredAt,
        String description
) {
}
