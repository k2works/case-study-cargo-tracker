package com.example.cargotracker.security.domain.model.valueobjects;

/**
 * システムのロール。
 *
 * <p>定義の正典は {@code docs/design/non_functional.md} の RBAC ロール定義である。
 * 値を追加・変更する場合は先にそちらを更新すること。
 */
public enum Role {
    /** 営業担当者。見積・荷主・予約を扱う。 */
    SALES,
    /** 荷主。自分の予約と追跡を参照する。 */
    SHIPPER,
    /** 荷受人。追跡を参照する。 */
    CONSIGNEE,
    /** 経路設計者。航路と経路割り当てを扱う。 */
    ROUTER,
    /** 追跡管理者。追跡・例外・通関を扱う。 */
    TRACKER,
    /** 荷役作業員。荷役と通関を扱う。 */
    HANDLER,
    /** 経理担当者。請求と精算を扱う。 */
    BILLING,
    /** 管理者。 */
    ADMIN;

    /** Spring Security が用いる {@code ROLE_} 接頭辞付きの権限名を返す。 */
    public String authority() {
        return "ROLE_" + name();
    }
}
