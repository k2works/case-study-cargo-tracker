package com.example.cargotracker.billing.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 請求書を取り消す（UC18）。
 *
 * <p><b>理由は必須。</b> 取り消した請求書は荷主にも見えなくなるので、
 * 何が起きたかを追えなければ、あとから誰も確かめられない。</p>
 *
 * <p><b>取り消したら再発行しない</b>（不変条件 6）。出し直すときは新規に
 * 発行する——生き返らせると、荷主に一度取り消しを伝えたものがまた有効になる。</p>
 */
public record VoidInvoiceCommand(
        @TargetEntityId String invoiceId,
        String reason,
        String voidedBy) {
}
