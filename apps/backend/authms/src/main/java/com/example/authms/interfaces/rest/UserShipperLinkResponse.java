package com.example.authms.interfaces.rest;

import com.example.authms.application.internal.UserShipperLinkResult;

/** 利用者と荷主の紐付け照会応答（US33）。 */
public record UserShipperLinkResponse(boolean linked, Long shipperId) {

    public static UserShipperLinkResponse from(UserShipperLinkResult result) {
        return new UserShipperLinkResponse(result.linked(), result.shipperId());
    }
}
