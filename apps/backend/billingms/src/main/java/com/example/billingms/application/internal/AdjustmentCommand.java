package com.example.billingms.application.internal;

import java.math.BigDecimal;

/**
 * 料金調整の入力（[ADR-027] 決定 6）。
 *
 * <p><strong>金額は経理担当者が決める。</strong>どれだけ減額するかは荷主との関係で決まる
 * 話であり、規則にできない。画面は根拠（誤配・例外）を出し、金額はここから受け取る。
 *
 * @param description 調整の内容（根拠）。<strong>空は断る</strong>
 * @param amountValue 金額。減額は負、補償費用は正
 */
public record AdjustmentCommand(String description, BigDecimal amountValue) {
}
