package com.example.bookingms.interfaces.rest;

/** 入力の誤りを画面に伝える。理由を返さないと、利用者は何を直せばよいか分からない。 */
public record ErrorResponse(String message) {
}
