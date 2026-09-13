package com.example.cargotracker.billing.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 記録した入金を取り消す（UC18 / US23。IT15 引き継ぎ 3）。
 *
 * <p><b>請求書の取消とは別の操作である。</b> 請求書は正しく、入金の記録だけが
 * 誤っている。マニュアル 17 章が「いまのところ運用で引き取る」と書いていたものを
 * 業務の操作にする。</p>
 *
 * @param paymentId <b>どの入金を取り消すか</b>。状態だけで通すと、どの入金を
 *     取り消したのかが残らない
 * @param reason 取消の理由。<b>必須</b>——追えない記録を残さない
 */
public record VoidPaymentCommand(
        @TargetEntityId String invoiceId,
        String paymentId,
        String reason,
        String voidedBy) {
}
