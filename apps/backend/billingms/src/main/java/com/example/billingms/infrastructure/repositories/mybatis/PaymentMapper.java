package com.example.billingms.infrastructure.repositories.mybatis;

import com.example.billingms.domain.projections.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入金履歴 Read Model 用の MyBatis Mapper（US23 / IT7 T4.3）。
 *
 * <p>SQL は {@code resources/mapper/PaymentMapper.xml} で定義する。
 * IT7 は完全一致入金のみ受理（1 invoice 1 payment）、IT8 で部分入金対応予定。</p>
 */
@Mapper
public interface PaymentMapper {

    /**
     * PaymentRecordedEvent 受信時の入金履歴 INSERT。
     */
    void insertPayment(@Param("paymentId") String paymentId,
                       @Param("invoiceId") String invoiceId,
                       @Param("paidAmount") BigDecimal paidAmount,
                       @Param("currency") String currency,
                       @Param("paidAt") LocalDateTime paidAt,
                       @Param("paymentMethod") String paymentMethod,
                       @Param("externalReference") String externalReference);

    /** invoiceId 単位の入金履歴（時系列）。S23 詳細画面・S22 一覧の支払履歴で利用。 */
    List<Payment> findByInvoiceId(@Param("invoiceId") String invoiceId);
}
