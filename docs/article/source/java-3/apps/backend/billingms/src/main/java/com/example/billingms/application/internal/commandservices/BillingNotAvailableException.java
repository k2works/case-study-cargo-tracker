package com.example.billingms.application.internal.commandservices;

/**
 * 料金算出の対象でない予約に対する操作（[ADR-027] 決定 5）。
 *
 * <p>まだ運び終えていない予約に請求書は出せない。<strong>画面で出し分けるだけでは
 * 守れない</strong>——URL を直接開かれる。
 */
public class BillingNotAvailableException extends RuntimeException {

    public BillingNotAvailableException(String message) {
        super(message);
    }
}
