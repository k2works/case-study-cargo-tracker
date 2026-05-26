package com.example.bookingms.domain.model;

import java.time.LocalDateTime;

/**
 * 輸送区間（Leg、US11 / Booking Context）。値オブジェクト。
 *
 * <p>確定した旅程（{@code CargoItinerary} 相当）を構成する 1 区間。航海番号・積込港・荷降し港と
 * その日時を保持する。routingms の経路確定（{@code RouteConfirmedEvent}）から写し取って確定する。</p>
 */
public record Leg(
        String voyageNumber,
        String loadUnlocode,
        String unloadUnlocode,
        LocalDateTime loadTime,
        LocalDateTime unloadTime
) {
}
