package com.example.authms.interfaces.rest;

import com.example.authms.application.internal.FindUserShipperLinkUseCase;
import com.example.shared.auth.AuthenticatedUser;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 利用者と荷主の紐付けを返すサービス間 API（US33）。
 *
 * <p>呼ぶのは bookingms / trackingms であり、人ではない。人のロールでは開かない。
 */
@RestController
@RequestMapping("/api/v1/internal/user-shipper-links")
public class UserShipperLinkController {

    private static final Set<String> TRUSTED_SERVICE_PRINCIPALS =
            Set.of("system:bookingms", "system:trackingms");

    private final FindUserShipperLinkUseCase links;

    public UserShipperLinkController(FindUserShipperLinkUseCase links) {
        this.links = links;
    }

    @GetMapping("/{username}")
    public UserShipperLinkResponse find(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @PathVariable String username) {
        requireTrustedService(userId);

        return UserShipperLinkResponse.from(links.find(username));
    }

    private void requireTrustedService(String userId) {
        if (!AuthenticatedUser.of(userId, null).isOneOf(TRUSTED_SERVICE_PRINCIPALS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }
}
