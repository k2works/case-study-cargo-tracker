package com.example.cargotracker.billingms.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * payment テーブルの MyBatis Mapper（US23）。
 */
@Mapper
public interface PaymentMapper {

    void insert(PaymentRecord payment);

    List<PaymentRecord> findByInvoiceId(@Param("invoiceId") String invoiceId);

    /**
     * invoice テーブルを精算書発行済みに更新する（INVOICED 状態遷移）。
     */
    void updateInvoiceIssued(
            @Param("invoiceId") String invoiceId,
            @Param("invoiceNumber") String invoiceNumber,
            @Param("paymentDue") LocalDate paymentDue,
            @Param("billingStatus") String billingStatus);

    /**
     * invoice テーブルを精算完了に更新する（PAID 状態遷移）。
     */
    void updateInvoicePaid(
            @Param("invoiceId") String invoiceId,
            @Param("billingStatus") String billingStatus);

    List<InvoiceRecord> findOverdueInvoices();
}
