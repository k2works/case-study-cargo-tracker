package com.example.cargotracker.billing.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 請求書を発行する（UC18 / US23 §受入基準 1）。
 *
 * <p><b>支払期限は載せない。</b> 発行日 + 30 日は集約が決める（不変条件 3）。
 * コマンドで受け取ると、画面が期限を自由に決められることになり、同じ規則が
 * 2 か所に書かれる。</p>
 *
 * <p><b>発行日も載せない。</b> 「いつ発行したか」は集約が {@code Clock} から
 * 採る——業務タイムゾーンで決めるので、画面の時計に依存させない。</p>
 */
public record IssueInvoiceCommand(
        @TargetEntityId String invoiceId,
        String issuedBy) {
}
