package com.example.billingms.infrastructure.repositories.mybatis;

import com.example.billingms.domain.projections.WebhookProcessed;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Stripe webhook 冪等性管理 Mapper（IT9 A1.2 / ADR-0020 / US26）。
 *
 * <p>SQL は {@code resources/mapper/WebhookProcessedMapper.xml} で定義する。</p>
 */
@Mapper
public interface WebhookProcessedMapper {

    /** Stripe Event ID で既存レコードを検索（冪等性チェック）。 */
    WebhookProcessed findByEventId(@Param("eventId") String eventId);

    /** 新規 webhook 受信を記録（processing_status=RECEIVED）。 */
    void insertReceived(@Param("eventId") String eventId,
                        @Param("provider") String provider,
                        @Param("eventType") String eventType,
                        @Param("payloadHash") String payloadHash);

    /** 処理完了をマーク（processing_status=PROCESSED + processed_at）。 */
    void markProcessed(@Param("eventId") String eventId,
                       @Param("invoiceId") String invoiceId);

    /** 処理失敗をマーク（processing_status=FAILED + error_reason）。 */
    void markFailed(@Param("eventId") String eventId,
                    @Param("errorReason") String errorReason);
}
