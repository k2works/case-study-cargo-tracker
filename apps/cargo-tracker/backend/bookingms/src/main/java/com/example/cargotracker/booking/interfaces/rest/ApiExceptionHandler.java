package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.application.port.RouteCandidateFinder;
import com.example.cargotracker.booking.interfaces.rest.ShipperController.DuplicateShipperEmailException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API のエラー対応表（architecture_backend.md「例外と HTTP の対応」）。
 *
 * <p><b>共通の対応は共有カーネルが 1 つ持つ</b>（IT9 レビュー H.2）。ここに残すのは
 * <b>予約にしかない断り方</b>だけである——荷主メールの重複（409）と、経路設計
 * サービスへ問い合わせできなかったとき（503）。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler
        extends com.example.cargotracker.shared.interfaces.rest.AbstractApiExceptionHandler {


    @ExceptionHandler(DuplicateShipperEmailException.class)
    public ResponseEntity<Map<String, Object>> onDuplicateEmail(DuplicateShipperEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(CODE, "SHIPPER_EMAIL_DUPLICATE", MESSAGE, e.getMessage()));
    }

    /**
     * 経路設計サービスに問い合わせられなかった（US08）。
     *
     * <p><b>500 にも 200 の空一覧にもしない。</b> 空にすると「候補が無い」と読まれ、
     * 経路設計者は条件を変え続ける。503 は「あとでもう一度」を意味するので、
     * 次にすべきことが伝わる。</p>
     */
    @ExceptionHandler(RouteCandidateFinder.RouteSearchUnavailable.class)
    public ResponseEntity<Map<String, Object>> onRouteSearchUnavailable(
            RouteCandidateFinder.RouteSearchUnavailable e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(CODE, "ROUTE_SEARCH_UNAVAILABLE", MESSAGE,
                        "経路設計サービスに問い合わせできませんでした。"
                                + "しばらくしてからもう一度お試しください"));
    }
}
