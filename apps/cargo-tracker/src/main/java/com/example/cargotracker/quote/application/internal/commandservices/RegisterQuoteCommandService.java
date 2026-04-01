package com.example.cargotracker.quote.application.internal.commandservices;

import com.example.cargotracker.quote.application.internal.outboundservices.QuoteRouteProviderPort;
import com.example.cargotracker.quote.domain.model.aggregates.Quote;
import com.example.cargotracker.quote.domain.model.aggregates.QuoteId;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import com.example.cargotracker.quote.domain.repository.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 見積登録ユースケース。
 */
@Service
@Transactional
public class RegisterQuoteCommandService {

    private final QuoteRouteProviderPort quoteRouteProviderPort;
    private final QuoteRepository quoteRepository;

    public RegisterQuoteCommandService(QuoteRouteProviderPort quoteRouteProviderPort,
                                       QuoteRepository quoteRepository) {
        this.quoteRouteProviderPort = quoteRouteProviderPort;
        this.quoteRepository = quoteRepository;
    }

    /**
     * 見積を登録する。
     *
     * @param command 見積登録コマンド
     * @return 発行された見積
     * @throws NoRouteAvailableException ルート候補が 0 件の場合
     */
    public Quote register(RegisterQuoteCommand command) {
        QuoteCondition condition = new QuoteCondition(
                command.originLocode(),
                command.destinationLocode(),
                command.requestedArrivalDate(),
                command.cargoType(),
                command.weightKg()
        );

        List<RouteOption> routeOptions = quoteRouteProviderPort.findRouteOptions(condition);
        if (routeOptions.isEmpty()) {
            throw new NoRouteAvailableException(command.originLocode(), command.destinationLocode());
        }

        QuoteId quoteId = QuoteId.generate();
        Quote quote = Quote.issue(quoteId, condition, routeOptions);
        quoteRepository.save(quote);
        return quote;
    }
}
