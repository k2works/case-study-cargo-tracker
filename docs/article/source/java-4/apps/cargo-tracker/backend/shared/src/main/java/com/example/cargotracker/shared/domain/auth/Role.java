package com.example.cargotracker.shared.domain.auth;

/** ロール（domain-model.md の Role）。増やしたら画面の到達性検査も同時に直す。 */
public enum Role {
    ROLE_SHIPPER,
    ROLE_SALES,
    ROLE_ROUTING,
    ROLE_TRACKER,
    ROLE_HANDLER,
    ROLE_ACCOUNTANT,
    ROLE_ADMIN
}
