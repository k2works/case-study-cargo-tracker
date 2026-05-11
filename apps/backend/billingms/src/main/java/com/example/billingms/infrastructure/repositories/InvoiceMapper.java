package com.example.billingms.infrastructure.repositories;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 請求書 MyBatis マッパー
 */
@Mapper
public interface InvoiceMapper {
    void insert(InvoiceRecord record);
    Optional<InvoiceRecord> findById(@Param("id") Long id);
    Optional<InvoiceRecord> findByBookingId(@Param("bookingId") String bookingId);
    void updateStatus(InvoiceRecord record);

    void insertLineItem(InvoiceLineItemRecord record);
    List<InvoiceLineItemRecord> findLineItemsByInvoiceId(@Param("invoiceId") Long invoiceId);
}
