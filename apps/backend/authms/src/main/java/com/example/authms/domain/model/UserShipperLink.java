package com.example.authms.domain.model;

/**
 * authms の利用者と bookingms の荷主 ID の紐付け（US33）。
 *
 * <p>荷主名や利用者名の文字列一致で推測しない。明示的な行があるときだけ紐付いている。
 */
public record UserShipperLink(String username, Long shipperId) {

    public UserShipperLink {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("利用者 ID は必須です");
        }
        if (shipperId == null || shipperId <= 0) {
            throw new IllegalArgumentException("荷主 ID は正の値です");
        }
    }
}
