package com.example.handlingms.domain.model;

/**
 * 荷役作業種別（domain-model.md：5 値）。
 *
 * <p>港湾での荷役作業を分類する。{@link HandlingActivity} 集約の不変条件として、
 * {@code LOAD} / {@code UNLOAD} は航海番号必須、{@code CLAIM} は荷受人確認必須。</p>
 */
public enum HandlingType {
    /** 受領（出発港での貨物受け取り） */
    RECEIVE,
    /** 積込（航海への積み込み） */
    LOAD,
    /** 荷降し（航海からの荷降ろし） */
    UNLOAD,
    /** 引取（最終港での引き渡し。荷受人確認必須） */
    CLAIM,
    /** 税関通過 */
    CUSTOMS
}
