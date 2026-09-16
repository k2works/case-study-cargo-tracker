package com.example.cargotracker.booking.domain.model.commands;

import java.math.BigDecimal;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 予約に、もとになった見積を結び付ける（US01 §受入基準 4・注 N12）。
 *
 * <p><b>予約の受付とは別の操作にする。</b> 見積を経ない予約のほうが多いので、
 * 受付そのものに見積を必須の入力として足さない。結び付けに失敗しても予約は
 * 残る——「見積と比べられない予約」は業務として成り立つが、「受け付けられ
 * なかった予約」は成り立たない。</p>
 *
 * @param quotedAmount 見積時の概算。<b>請求との差を経理が見る</b>（S61）
 */
public record LinkQuotationCommand(
        @TargetEntityId String bookingId,
        String quotationId,
        BigDecimal quotedAmount,
        String currency,
        String linkedBy) {
}
