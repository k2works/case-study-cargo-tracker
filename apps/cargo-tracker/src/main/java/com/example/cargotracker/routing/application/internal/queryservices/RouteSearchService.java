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
 * <p>両メソッドとも以下のフィルタリングを適用する:
 * <ul>
 *   <li>希望着日フィルタ: {@code estimatedArrival <= requestedArrivalDate} のルートのみ返す</li>
 *   <li>貨物種別フィルタ: {@code supportedCargoTypes} にクエリの貨物種別が含まれるルートのみ返す</li>
 * </ul>
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
     * @return フィルタ済みルート候補リスト
     * @throws BookingDataNotFoundException 指定した予約が存在しない場合
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

        return applyFilters(routeProviderPort.findRoutes(query), query);
    }

    /**
     * 検索条件を直接指定してルート候補を検索する。
     *
     * @param query ルート検索条件
     * @return フィルタ済みルート候補リスト
     */
    public List<RouteCandidate> searchByCondition(RouteSearchQuery query) {
        return applyFilters(routeProviderPort.findRoutes(query), query);
    }

    private List<RouteCandidate> applyFilters(List<RouteCandidate> candidates, RouteSearchQuery query) {
        return candidates.stream()
            .filter(c -> !c.estimatedArrival().isAfter(query.requestedArrivalDate()))
            .filter(c -> c.supportedCargoTypes().contains(query.cargoType()))
            .sorted(java.util.Comparator
                .comparingInt(RouteCandidate::transitDays)
                .thenComparing(RouteCandidate::estimatedPrice))
            .toList();
    }
}
