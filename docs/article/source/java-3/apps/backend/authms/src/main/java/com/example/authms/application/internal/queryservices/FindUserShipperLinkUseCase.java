package com.example.authms.application.internal.queryservices;

import org.springframework.stereotype.Service;

import com.example.authms.application.port.UserRepository;

/** 利用者 ID から、明示的に紐付いた荷主 ID を照会する（US33）。 */
@Service
public class FindUserShipperLinkUseCase {

    private final UserRepository users;

    public FindUserShipperLinkUseCase(UserRepository users) {
        this.users = users;
    }

    public UserShipperLinkResult find(String username) {
        return users.findLinkedShipperId(username)
                .map(UserShipperLinkResult::linked)
                .orElseGet(UserShipperLinkResult::unlinked);
    }
}
