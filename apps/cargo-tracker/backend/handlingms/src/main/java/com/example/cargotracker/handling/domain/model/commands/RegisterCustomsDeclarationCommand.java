package com.example.cargotracker.handling.domain.model.commands;

import java.time.Instant;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 通関申告を登録する（UC21 / US29 §受入基準 1）。
 *
 * <p><b>申告番号は利用者が持ち込む。</b> 採番するのは税関なので、こちらでは作らない。
 * 書式も検査しない（不変条件 1）——国ごとに違うものを、こちらの想像で縛らない。</p>
 *
 * <p><b>予約 ID を載せる。</b> 契約イベントが billingms へ渡す（請求は追跡番号では
 * 引けない）。application 層が {@code CargoSnapshot} から解決して載せる。</p>
 */
public record RegisterCustomsDeclarationCommand(
        @TargetEntityId String declarationNumber,
        String trackingNumber,
        String bookingId,
        Instant declaredAt,
        String registeredBy) {
}
