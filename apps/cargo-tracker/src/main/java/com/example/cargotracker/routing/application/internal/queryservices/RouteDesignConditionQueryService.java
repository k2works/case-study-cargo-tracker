package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.domain.model.RouteDesignCondition;

import java.util.UUID;

/**
 * 経路設計条件照会アプリケーションサービス。
 *
 * <p>予約 ID をキーに {@link BookingQueryPort} 経由で予約情報を取得し、
 * routing コンテキストの {@link RouteDesignCondition} に変換して返す。
 */
public class RouteDesignConditionQueryService {

    private final BookingQueryPort bookingQueryPort;

    public RouteDesignConditionQueryService(BookingQueryPort bookingQueryPort) {
        this.bookingQueryPort = bookingQueryPort;
    }

    /**
     * 予約 ID から経路設計条件を取得する。
     *
     * @param bookingId 予約 ID
     * @return 経路設計条件
     * @throws BookingDataNotFoundException 指定した予約が存在しない場合
     */
    public RouteDesignCondition findByBookingId(UUID bookingId) {
        var snapshot = bookingQueryPort.findById(bookingId)
            .orElseThrow(() -> new BookingDataNotFoundException(bookingId));

        return new RouteDesignCondition(
            bookingId,
            snapshot.originLocode(),
            snapshot.destinationLocode(),
            snapshot.requestedArrivalDate(),
            snapshot.cargoType(),
            snapshot.weightKg()
        );
    }
}
