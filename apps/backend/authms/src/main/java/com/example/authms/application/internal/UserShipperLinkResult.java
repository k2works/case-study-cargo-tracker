package com.example.authms.application.internal;

/** 利用者と荷主の紐付け照会結果（US33）。 */
public record UserShipperLinkResult(boolean linked, Long shipperId) {

    public static UserShipperLinkResult linked(Long shipperId) {
        return new UserShipperLinkResult(true, shipperId);
    }

    public static UserShipperLinkResult unlinked() {
        return new UserShipperLinkResult(false, null);
    }
}
