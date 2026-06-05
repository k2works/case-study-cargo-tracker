package com.example.billingms.infrastructure.repositories.mybatis;

import com.example.billingms.domain.services.InvoiceNumberSequenceRepository;
import org.springframework.stereotype.Component;

/**
 * {@link InvoiceNumberSequenceRepository} の MyBatis 実装（IT7 review M2 対応）。
 *
 * <p>{@link InvoiceSummaryMapper#findMaxInvoiceNumberSequenceForDate(String)} を呼んで
 * 当日採番済の最大シーケンス番号を取得する。本クラスのみが Mapper に依存し、
 * ドメインサービス側は {@link InvoiceNumberSequenceRepository} ポートのみを参照する。</p>
 */
@Component
public class MybatisInvoiceNumberSequenceRepository implements InvoiceNumberSequenceRepository {

    private final InvoiceSummaryMapper mapper;

    public MybatisInvoiceNumberSequenceRepository(InvoiceSummaryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Integer findMaxSequenceForDate(String yyyymmdd) {
        return mapper.findMaxInvoiceNumberSequenceForDate(yyyymmdd);
    }
}
