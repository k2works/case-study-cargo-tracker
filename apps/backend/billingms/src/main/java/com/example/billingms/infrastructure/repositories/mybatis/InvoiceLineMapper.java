package com.example.billingms.infrastructure.repositories.mybatis;

import com.example.billingms.domain.projections.InvoiceLine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 請求書明細 Read Model 用の MyBatis Mapper（US21 / US22 / IT7 タスク 2.5）。
 *
 * <p>SQL は {@code resources/mapper/InvoiceLineMapper.xml} で定義する。
 * line_type 駆動設計（ADR-0015）に従い、BASIC / DISCOUNT / ADJUSTMENT / SURCHARGE の
 * いずれかの行を追加する。</p>
 */
@Mapper
public interface InvoiceLineMapper {

    void insertInvoiceLine(@Param("invoiceId") String invoiceId,
                           @Param("lineSeq") int lineSeq,
                           @Param("lineType") String lineType,
                           @Param("description") String description,
                           @Param("amount") BigDecimal amount,
                           @Param("reasonCode") String reasonCode);

    List<InvoiceLine> findByInvoiceId(@Param("invoiceId") String invoiceId);

    /** 指定 invoiceId の最大 line_seq（次の seq 算出用）。 */
    Integer findMaxLineSeq(@Param("invoiceId") String invoiceId);
}
