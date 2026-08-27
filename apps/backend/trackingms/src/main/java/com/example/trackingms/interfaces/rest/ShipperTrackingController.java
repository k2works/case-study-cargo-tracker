package com.example.trackingms.interfaces.rest;

import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import com.example.trackingms.application.internal.ShipperTrackingDetail;
import com.example.trackingms.application.internal.ShipperTrackingQueryResult;
import com.example.trackingms.application.internal.ShipperTrackingQueryUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** ログインした荷主が、自社貨物だけを追跡する API（US33）。 */
@RestController
@RequestMapping("/api/v1/shipper/tracking")
public class ShipperTrackingController {

    private static final String NOT_FOUND_MESSAGE = "自社の貨物として確認できません";

    private final ShipperTrackingQueryUseCase query;

    public ShipperTrackingController(ShipperTrackingQueryUseCase query) {
        this.query = query;
    }

    /** 自社貨物一覧。紐付けが無い場合も 200 で案内を返す。 */
    @GetMapping
    public ShipperTrackingQueryResult list(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireShipper(userId, roles);

        return query.list(userId);
    }

    /** 自社貨物の詳細。他社貨物は存在していても 404 にする。 */
    @GetMapping("/{trackingNumber}")
    public ShipperTrackingDetail detail(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String trackingNumber) {
        requireShipper(userId, roles);

        return query.detail(userId, trackingNumber)
                .orElseThrow(ShipperTrackingController::notFound);
    }

    private static void requireShipper(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_SHIPPER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    private static ShipperTrackingNotFoundException notFound() {
        return new ShipperTrackingNotFoundException();
    }

    static class ShipperTrackingNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ShipperTrackingNotFoundException() {
            super(NOT_FOUND_MESSAGE);
        }
    }

    @ExceptionHandler(ShipperTrackingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ShipperTrackingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    public record ErrorResponse(String message) {
    }
}
