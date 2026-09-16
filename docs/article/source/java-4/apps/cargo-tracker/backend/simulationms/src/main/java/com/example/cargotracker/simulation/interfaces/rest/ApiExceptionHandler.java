package com.example.cargotracker.simulation.interfaces.rest;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API のエラー対応表（architecture_backend.md「例外と HTTP の対応」）。
 *
 * <p><b>中身は共有カーネルが 1 つ持つ。</b> ここに写すと、対応表がサービスの数だけに
 * なって片方だけ直る。サービス固有の断り方が要るときだけ、このクラスに
 * {@code @ExceptionHandler} を足す。</p>
 *
 * <p><b>Event Sourcing でなくても対応表は要る</b>（[ADR-0020] 決定 3）。
 * 知らないシナリオ・二重実行・順を飛ばした工程はいずれもドメイン例外で断るので、
 * 対応表が無いと利用者には 500 としか出ない。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler
        extends com.example.cargotracker.shared.interfaces.rest.AbstractApiExceptionHandler {
}
