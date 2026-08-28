package com.example.trackingms.application.internal.outboundservices.acl;

import java.util.Optional;

/** 利用者と荷主の紐付けを引く出力ポート。 */
public interface UserShipperLinkFinder {

    /** 紐付いた荷主 ID。紐付いていなければ空。 */
    Optional<Long> findLinkedShipperId(String username);
}
