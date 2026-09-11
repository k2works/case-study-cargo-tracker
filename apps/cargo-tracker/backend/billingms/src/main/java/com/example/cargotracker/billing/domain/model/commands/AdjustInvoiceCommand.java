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
 * @param amount 調整額。<b>符号で向きを表す</b>——減額は負、補償費用は正
 * @param basisExceptionId 根拠になった例外の ID（任意。留置は申告番号を指す）
 */
public record AdjustInvoiceCommand(
        @TargetEntityId String invoiceId,
        BigDecimal amount,
        String reason,
        String basisExceptionId,
        String adjustedBy) {
}
