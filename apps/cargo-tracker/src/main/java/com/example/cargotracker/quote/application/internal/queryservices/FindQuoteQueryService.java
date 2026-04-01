package com.example.cargotracker.quote.application.internal.queryservices;

import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;
import com.example.cargotracker.quote.domain.repository.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 見積照会ユースケース。
 */
@Service
@Transactional(readOnly = true)
public class FindQuoteQueryService {

    private final QuoteRepository quoteRepository;

    public FindQuoteQueryService(QuoteRepository quoteRepository) {
        this.quoteRepository = quoteRepository;
    }

    /**
     * ID で見積を取得する。
     *
     * @param id 見積 ID
     * @return 見積
     * @throws QuoteNotFoundException 見積が存在しない場合
     */
    public Quote findById(QuoteId id) {
        return quoteRepository.findById(id)
                .orElseThrow(() -> new QuoteNotFoundException(id.value().toString()));
    }

    /**
     * すべての見積を取得する。
     *
     * @return 見積一覧
     */
    public List<Quote> findAll() {
        return quoteRepository.findAll();
    }
}
