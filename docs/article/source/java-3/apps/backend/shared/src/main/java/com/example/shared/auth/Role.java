package com.example.shared.auth;

import java.util.Optional;

/**
 * 業務ロール。IT1 で 7 値に確定した（ui_design.md / data-model.md と同一）。
 *
 * <p>Gateway と全サービスが同じ値を見るため共有カーネルに置く。サービスごとに文字列で
 * 持つと、ロール名を変えたときに誰も落ちない。
 *
 * <p>経路設計者は営業担当者と別のアクターであり、兼務させると営業が航海スケジュール登録・
 * 経路確定まで行えてしまい職掌分離が崩れるため、独立したロールとする。
 */
public enum Role {
    ROLE_SHIPPER,
    ROLE_SALES,
    ROLE_ROUTING,
    ROLE_HANDLER,
    ROLE_TRACKER,
    ROLE_ACCOUNTANT,
    ROLE_ADMIN;

    /** 名前からロールを解決する。未知の名前は空を返す（例外にしない）。 */
    public static Optional<Role> of(String name) {
        for (Role role : values()) {
            if (role.name().equals(name)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
