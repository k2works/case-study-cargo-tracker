package com.example.cargotracker.booking.infrastructure.query;

import com.example.cargotracker.booking.infrastructure.persistence.QuotationMapper;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindQuotationQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.QuotationCandidateView;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.QuotationView;
import java.util.List;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 見積の読み取り（S13 / US01）。 */
@Component
public class QuotationQueryHandler {

    private final QuotationMapper quotations;

    public QuotationQueryHandler(QuotationMapper quotations) {
        this.quotations = quotations;
    }

    @QueryHandler
    public QuotationView handle(FindQuotationQuery query) {
        QuotationMapper.QuotationRow row = quotations.find(query.quotationId());
        if (row == null) {
            return null;
        }
        List<QuotationCandidateView> candidates =
                quotations.findCandidates(row.quotationId()).stream()
                        .map(candidate -> new QuotationCandidateView(
                                candidate.candidateSeq(), candidate.voyageNumbers(),
                                // **列が無かったころの行は経由港を持たない。**
                                // 空文字にせず、画面が「分からない」を出せる形で渡す。
                                candidate.ports(), candidate.transitDays(), candidate.estimatedCost(),
                                candidate.estimatedCurrency(), candidate.overdueDays()))
                        .toList();

        return new QuotationView(row.quotationId(), row.originUnLocode(),
                row.destinationUnLocode(), row.arrivalDeadline(), row.cargoType(),
                row.weightKg(),
                row.estimatedAmount(), row.estimatedCurrency(), row.validUntil(),
                // **サーバが数える**（US01 §受入基準 5）。画面に数え直させると、
                // 「間に合う候補がある」の判断が 2 か所に書かれる。
                candidates.stream().anyMatch(candidate -> candidate.overdueDays() == 0),
                row.createdBy(), row.createdAt(), candidates);
    }
}
