package com.example.authms.application.internal.commandservices;

import com.example.authms.application.port.UserRepository;
import com.example.authms.domain.model.User;
import com.example.authms.domain.model.UserShipperLink;
import com.example.shared.auth.Role;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 管理者による利用者と荷主 ID の紐付け管理。
 */
@Service
public class ManageUserShipperLinkUseCase {

    private final UserRepository users;

    public ManageUserShipperLinkUseCase(UserRepository users) {
        this.users = users;
    }

    public Optional<UserShipperLink> link(String username, Long shipperId) {
        UserShipperLink link = new UserShipperLink(username, shipperId);
        return users.findByUsername(username)
                .map(user -> saveIfShipper(user, link));
    }

    public Optional<UserShipperLink> unlink(String username) {
        return users.findByUsername(username)
                .flatMap(user -> users.removeShipperLink(user.username()));
    }

    private UserShipperLink saveIfShipper(User user, UserShipperLink link) {
        if (!user.roles().contains(Role.ROLE_SHIPPER)) {
            throw new IllegalArgumentException("荷主ロールの利用者だけを荷主に紐付けられます");
        }
        return users.saveShipperLink(link);
    }
}
