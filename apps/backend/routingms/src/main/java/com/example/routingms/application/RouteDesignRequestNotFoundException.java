package com.example.routingms.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 経路設計依頼が存在しない場合の例外（US08）。
 *
 * <p>指定された予約 ID に対応する経路設計依頼（route_design_request）が
 * routingms に未記録のときに送出する。REST 層では 404 Not Found を返す。</p>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RouteDesignRequestNotFoundException extends RuntimeException {

    public RouteDesignRequestNotFoundException(String bookingId) {
        super("経路設計依頼が見つかりません: " + bookingId);
    }
}
