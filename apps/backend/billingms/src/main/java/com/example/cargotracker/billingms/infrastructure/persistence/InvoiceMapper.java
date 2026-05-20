package com.example.cargotracker.billingms.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * invoice テーブルへの MyBatis アクセサ（US21）。
 *
 * <p>XML マッパー {@code mybatis/mapper/InvoiceMapper.xml} と対応する。</p>
 */
@Mapper
public interface InvoiceMapper {

    void insert(InvoiceRecord invoice);

    Optional<InvoiceRecord> findById(@Param("invoiceId") String invoiceId);

    List<InvoiceRecord> findAll();

    List<InvoiceRecord> findByStatus(@Param("billingStatus") String billingStatus);

    void updateCharge(
            @Param("invoiceId") String invoiceId,
            @Param("basicAmount") BigDecimal basicAmount,
            @Param("discountAmount") BigDecimal discountAmount,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("billingStatus") String billingStatus);
}
