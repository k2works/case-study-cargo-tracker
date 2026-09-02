package com.example.bookingms.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 予約の 2 つの入口に共通する、失敗の翻訳。
 *
 * <p>{@code @ExceptionHandler} はそれを書いたコントローラにしか効かない。入口を分けたときに
 * 各コントローラへ写すと、<strong>片方だけ直る</strong>形になる——同じ規則が 2 か所にある状態は、
 * IT6 のふりかえりで最も多かった欠陥の形である。
 *
 * <p>対象は明示する。すべてのコントローラに掛けると、いま同じ翻訳でよいかを確かめていない
 * 入口（荷主の登録など）まで巻き込む。
 */
@RestControllerAdvice(assignableTypes = {CargoBookingController.class, CargoRoutingController.class})
public class BookingErrorHandlers {

    /**
     * 入力の誤りは理由を添えて 400 で返す。
     *
     * <p>理由を返さないと、営業担当者は「登録したのに一覧に出ない」としか見えない。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(UserFacingMessage.of(e)));
    }

    /**
     * 依頼できない状態への依頼は 409 で返す。
     *
     * <p>入力の誤り（400）ではない。入力は正しく、予約の状態がその操作を許さない。
     * 400 で返すと、画面は「入力を直してください」と伝えることになる。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(UserFacingMessage.of(e)));
    }
}
