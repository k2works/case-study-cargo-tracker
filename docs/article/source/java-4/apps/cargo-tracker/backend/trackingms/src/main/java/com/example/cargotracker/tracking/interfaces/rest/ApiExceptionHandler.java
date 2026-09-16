package com.example.cargotracker.tracking.interfaces.rest;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API のエラー対応表（architecture_backend.md「例外と HTTP の対応」）。
 *
 * <p><b>中身は共有カーネルが 1 つ持つ</b>（IT9 レビュー H.2）。ここに写すと、
 * 対応表が 4 つになって片方だけ直る。サービス固有の断り方が要るときだけ、
 * このクラスに {@code @ExceptionHandler} を足す。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler
        extends com.example.cargotracker.shared.interfaces.rest.AbstractApiExceptionHandler {
}
