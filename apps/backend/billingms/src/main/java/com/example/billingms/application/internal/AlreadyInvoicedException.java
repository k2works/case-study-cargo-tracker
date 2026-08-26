package com.example.billingms.application.internal;

/**
 * すでに精算書が発行されている予約への再発行（[ADR-027] 決定 4）。
 *
 * <p><strong>画面が押させないだけでは守れない</strong>——同時に 2 回押されることがある。
 */
public class AlreadyInvoicedException extends RuntimeException {

    public AlreadyInvoicedException(String message) {
        super(message);
    }
}
