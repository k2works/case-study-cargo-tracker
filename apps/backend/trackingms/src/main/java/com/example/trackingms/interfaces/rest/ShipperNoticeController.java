package com.example.trackingms.interfaces.rest;

import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import com.example.trackingms.application.internal.queryservices.ShipperNoticeQueryUseCase;
import com.example.trackingms.domain.model.valueobjects.ShipperNotice;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * ログインした荷主が、まだ見ていない知らせを受け取る API（US39）。
 *
 * <p><strong>画面はここを一定間隔で読む。</strong>ブラウザはメッセージ基盤を直接
 * 読めない——RabbitMQ を購読するのはこのサービスで、画面が読むのはその結果である。
 * 押し出す仕組み（WebSocket・SSE）を持ち込むと、Gateway と認証の経路が増える。
 */
@RestController
@RequestMapping("/api/v1/shipper/notifications")
public class ShipperNoticeController {

    private final ShipperNoticeQueryUseCase query;

    public ShipperNoticeController(ShipperNoticeQueryUseCase query) {
        this.query = query;
    }

    /** まだ見ていない知らせ。<strong>古い順</strong>——起きた順に出す。 */
    @GetMapping
    public ShipperNoticesResponse unread(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireShipper(userId, roles);

        return new ShipperNoticesResponse(query.unread(userId).stream()
                .map(ShipperNoticeResponse::from)
                .toList());
    }

    /**
     * そこまで読んだことにする。
     *
     * <p><strong>本文の番号を信じてよい。</strong>読んだ位置は利用者ごとに持ち、
     * 進めることしかできない（{@code NoticeWatermark#advanceTo}）——他人の位置も
     * 過去の位置も動かせない。
     */
    @PostMapping("/read")
    public ResponseEntity<Void> acknowledge(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody AcknowledgeRequest request) {
        requireShipper(userId, roles);

        query.acknowledge(userId, request.lastNoticeId());
        return ResponseEntity.noContent().build();
    }

    /**
     * <strong>認可を入力検証より先に置く</strong>（IT13 の学び）。後にすると、
     * 権限の無い相手に入力仕様を教えることになる。
     */
    private static void requireShipper(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_SHIPPER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    public record ShipperNoticesResponse(List<ShipperNoticeResponse> notifications) {
    }

    public record ShipperNoticeResponse(long id, String trackingNumber, Instant noticedAt,
            String message) {

        static ShipperNoticeResponse from(ShipperNotice notice) {
            return new ShipperNoticeResponse(notice.id(), notice.trackingNumber().value(),
                    notice.noticedAt(), notice.message());
        }
    }

    public record AcknowledgeRequest(long lastNoticeId) {
    }
}
