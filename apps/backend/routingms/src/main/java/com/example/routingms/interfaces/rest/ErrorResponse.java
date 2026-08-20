package com.example.routingms.interfaces.rest;

/** エラーの応答。文言はそのまま画面に出る。 */
public record ErrorResponse(String message) {
}
