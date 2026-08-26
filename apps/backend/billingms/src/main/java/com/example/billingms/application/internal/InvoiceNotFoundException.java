package com.example.billingms.application.internal;

/** 請求書が見つからない。**404 で返す**——「壊れた」ではなく「無い」である。 */
public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(String message) {
        super(message);
    }
}
