package com.example.trackingms.domain.model;

/**
 * 公開追跡照会トークンの利用主体ロール（ADR-0013 / ui_design.md L733）。
 *
 * <p>JWT の {@code role} claim に格納され、UI 側で表示権限・通知文面を切り替えるために使用する。</p>
 */
public enum TokenRole {
    SHIPPER,
    CONSIGNEE
}
