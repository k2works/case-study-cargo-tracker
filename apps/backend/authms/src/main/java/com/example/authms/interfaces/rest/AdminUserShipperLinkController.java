package com.example.authms.interfaces.rest;

import com.example.authms.application.internal.ManageUserShipperLinkUseCase;
import com.example.authms.domain.model.UserShipperLink;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 利用者と荷主 ID の紐付け管理（US33）。
 */
@RestController
@RequestMapping("/api/v1/admin/user-shipper-links")
public class AdminUserShipperLinkController {

    private final ManageUserShipperLinkUseCase links;

    public AdminUserShipperLinkController(ManageUserShipperLinkUseCase links) {
        this.links = links;
    }

    @PutMapping("/{username}")
    public UserShipperLinkResponse link(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String username,
            @RequestBody LinkRequest request) {
        requireAdmin(userId, roles);
        request.validate();

        return links.link(username, request.shipperId())
                .map(UserShipperLinkResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "指定された利用者が見つかりません"));
    }

    @DeleteMapping("/{username}")
    public UserShipperLinkResponse unlink(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String username) {
        requireAdmin(userId, roles);

        return links.unlink(username)
                .map(UserShipperLinkResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "指定された紐付けが見つかりません"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().build();
    }

    private void requireAdmin(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    private record LinkRequest(Long shipperId) {

        void validate() {
            if (shipperId == null || shipperId <= 0) {
                throw new IllegalArgumentException("荷主 ID は正の値です");
            }
        }
    }

    private record UserShipperLinkResponse(String username, Long shipperId) {

        static UserShipperLinkResponse from(UserShipperLink link) {
            return new UserShipperLinkResponse(link.username(), link.shipperId());
        }
    }
}
