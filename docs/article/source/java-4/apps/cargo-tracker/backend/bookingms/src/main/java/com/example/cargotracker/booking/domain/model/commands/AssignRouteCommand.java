package com.example.cargotracker.booking.domain.model.commands;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 経路候補から選んだ旅程を予約に紐づける（UC07 / US09）。
 *
 * <p><b>候補 ID ではなく旅程そのものを送る。</b> 経路候補はテーブルに持たないので
 * （data-model.md）、選んでから送るまでの間に航海が更新されうる。ID で送ると、
 * 送った先で別の内容の候補が確定する。</p>
 *
 * <p>選んだ内容が予約の経路仕様を満たすかは<b>集約が見る</b>。「候補は探索が作ったの
 * だから正しい」としない。探索と集約は別の判断で、API を直接叩く経路もある。</p>
 */
public record AssignRouteCommand(
        @TargetEntityId String bookingId,
        CargoItinerary itinerary,
        String assignedBy) {
}
