package com.example.cargotracker.quote.application.internal.queryservices;

/**
 * 見積が見つからない場合にスローされる例外。
 */
public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(String quoteId) {
        super("見積が見つかりません: " + quoteId);
    }
}
