package com.example.bookingms.application;

import com.example.bookingms.domain.projections.QuotationSummary;
import com.example.bookingms.infrastructure.repositories.mybatis.QuotationMapper;
import com.example.bookingms.interfaces.rest.dto.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 見積の Read Model 参照サービス（US01 / ADR-0008）。
 *
 * <p>サニタイズは {@link PageRequest} に集約されているため、本サービスは
 * 受け取った {@link PageRequest} の値をそのまま Mapper に委譲する。</p>
 */
@Service
@Transactional(readOnly = true)
public class QuotationQueryService {

    private final QuotationMapper quotationMapper;

    public QuotationQueryService(QuotationMapper quotationMapper) {
        this.quotationMapper = quotationMapper;
    }

    public QuotationSummary findById(String quotationId) {
        return quotationMapper.findById(quotationId);
    }

    public List<QuotationSummary> findAll(PageRequest pageRequest) {
        return quotationMapper.findAllPaged(pageRequest.offset(), pageRequest.size());
    }

    public long count() {
        return quotationMapper.countAll();
    }
}
