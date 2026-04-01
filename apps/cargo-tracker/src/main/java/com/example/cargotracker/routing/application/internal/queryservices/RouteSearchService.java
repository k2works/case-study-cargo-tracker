package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.application.internal.outboundservices.RouteProviderPort;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;

import java.util.List;
import java.util.UUID;

/**
 * ルート検索アプリケーションサービス。
 *
 * <p>予約番号起点でルートを検索する {@link #searchByBookingId} と、
 * 直接条件を指定して検索する {@link #searchByCondition} の 2 つの操作を提供する。
 *
 * <p>フィルタリングルールは後続タスク（2.3）で追加するため、現時点では全件を返す。
 */
public class RouteSearchService {

    private final BookingQueryPort bookingQueryPort;
    private final RouteProviderPort routeProviderPort;

    public RouteSearchService(BookingQueryPort bookingQueryPort, RouteProviderPort routeProviderPort) {
        this.bookingQueryPort = bookingQueryPort;
        this.routeProviderPort = routeProviderPort;
    }

    /**
     * 予約 ID をもとに予約の輸送条件を取得し、ルート候補を検索する。
     *
     * @param bookingId 予約 ID
     * @return ルート候補リスト
     * @throws BookingNotFoundException 指定した予約が存在しない場合
     */
    public List<RouteCandidate> searchByBookingId(UUID bookingId) {
        var snapshot = bookingQueryPort.findById(bookingId)
            .orElseThrow(() -> new BookingDataNotFoundException(bookingId));

        var query = new RouteSearchQuery(
            snapshot.originLocode(),
            snapshot.destinationLocode(),
            snapshot.requestedArrivalDate(),
            snapshot.cargoType(),
            snapshot.weightKg()
        );

        return routeProviderPort.findRoutes(query);
    }

    /**
     * 検索条件を直接指定してルート候補を検索する。
     *
     * @param query ルート検索条件
     * @return ルート候補リスト
     */
    public List<RouteCandidate> searchByCondition(RouteSearchQuery query) {
        return routeProviderPort.findRoutes(query);
    }
}
