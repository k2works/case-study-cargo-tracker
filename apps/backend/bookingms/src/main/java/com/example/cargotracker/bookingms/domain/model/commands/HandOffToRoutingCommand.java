package com.example.cargotracker.bookingms.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

import java.util.Objects;

/**
 * 予約引き渡しコマンド（US06 / UC04）。
 *
 * <p>仮受付状態（{@code PRELIMINARY}）の {@code Cargo} 集約を経路設計者に
 * 引き渡し、{@code BookingStatus.ROUTING}（経路設計中）へ遷移させる。</p>
 *
 * <p>受入条件:</p>
 * <ol>
 *   <li>予約番号を指定して予約情報（出発地・目的地・期限・貨物仕様）を確認できる</li>
 *   <li>経路設計依頼を実行すると、予約状態が「経路設計中」に更新される</li>
 *   <li>経路設計者に経路設計依頼の通知が送信される（内部 Saga トリガー）</li>
 *   <li>予約情報に不備がある場合、修正してから引き渡せる（UI 側の責務）</li>
 * </ol>
 */
public record HandOffToRoutingCommand(@TargetEntityId String bookingId) {

    public HandOffToRoutingCommand {
        Objects.requireNonNull(bookingId, "bookingId");
        if (bookingId.isBlank()) {
            throw new IllegalArgumentException("bookingId は空にできません");
        }
    }
}
