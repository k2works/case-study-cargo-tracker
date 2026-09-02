package com.example.billingms.interfaces.rest;

/**
 * 請求書の取り消し（赤伝・[ADR-028] 決定 3）。
 *
 * @param reason 取り消しの理由。<strong>必須</strong>——残らないと、あとから見て
 *        「二重発行の失敗」と区別できない
 */
public record VoidInvoiceRequest(String reason) {
}
