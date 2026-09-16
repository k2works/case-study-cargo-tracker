package com.example.cargotracker.billing.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 調整を取り消す（IT14 引き継ぎ C）。
 *
 * <p><b>誤入力は起きる。</b> 符号を取り違えた調整・根拠を間違えた調整が入った
 * まま請求書を発行すると、荷主に誤った額を請求することになる。発行（US23）を
 * 足す前に、戻せる道を作っておく。</p>
 *
 * <p><b>消さずに反対向きを積む。</b> 入れた調整を無かったことにすると、何が
 * 起きたかを追えなくなる——経理が確かめられない記録は根拠にならない。</p>
 *
 * @param adjustmentId 取り消す調整の識別子
 * @param reason 取り消す理由。<b>必須</b>（理由の読めない取り消しを残さない）
 */
public record ReverseAdjustmentCommand(
        @TargetEntityId String invoiceId,
        String adjustmentId,
        String reason,
        String reversedBy) {
}
