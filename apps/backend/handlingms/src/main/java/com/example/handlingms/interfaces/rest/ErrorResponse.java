package com.example.handlingms.interfaces.rest;

/**
 * 断る理由を伝える。
 *
 * <p>理由を返さないと、荷役作業員は「押したのに何も起きない」としか見えない。
 *
 * @param message 利用者に見せる文言
 */
public record ErrorResponse(String message) {
}
