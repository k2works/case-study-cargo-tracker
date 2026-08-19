package com.example.authms.domain.model;

/**
 * 業務ロール。IT1 で 7 値に確定した（ui_design.md / data-model.md と同一）。
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
    ROLE_ADMIN
}
