package com.example.cargotracker.billing.domain.model.commands;

import java.math.BigDecimal;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 料金を調整する（US21 §受入基準 6）。
 *
 * <p><b>根拠の例外を指す。</b> 減額も補償費用も「なぜその額か」が読めなければ、
 * あとから誰も確かめられない（US28 §8「誤配の事実は料金調整の根拠として参照
 * できる」の受け側）。</p>
 *
 * <p><b>調整には識別子を付ける</b>（IT14 引き継ぎ C）。付けないと、あとから
 * 「どの調整を取り消すのか」を指せない。金額と理由で指すと、同じ額・同じ理由の
 * 調整が 2 本あるときに取り違える。</p>
 *
 * @param adjustmentId 調整の識別子。<b>取り消すときの宛先</b>
 * @param amount 調整額。<b>符号で向きを表す</b>——減額は負、補償費用は正
 * @param basisExceptionId 根拠になった例外の ID（任意。留置は申告番号を指す）
 */
public record AdjustInvoiceCommand(
        @TargetEntityId String invoiceId,
        String adjustmentId,
        BigDecimal amount,
        String reason,
        String basisExceptionId,
        String adjustedBy) {
}
