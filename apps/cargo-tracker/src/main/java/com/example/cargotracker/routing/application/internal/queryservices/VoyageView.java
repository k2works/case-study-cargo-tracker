package com.example.cargotracker.routing.application.internal.queryservices;

import java.time.Instant;
import java.util.List;

/**
 * 航海の画面表示用データ（CQRS のクエリ側）。
 *
 * <p>寄港地・出発地・目的地は集約が {@code Schedule} から導く概念だが、
 * <strong>一覧のためだけに航海ごとの区間を読み直すと N+1 になる</strong>。
 * 読み取り側は SQL の集約関数で端点を求める。
 *
 * @param voyageNumber   航海番号
 * @param vesselName     船名
 * @param carrierName    運送会社
 * @param origin         出発地 UN/LOCODE
 * @param originName     出発地の名称
 * @param destination    目的地 UN/LOCODE
 * @param destinationName 目的地の名称
 * @param departureTime  出発時刻
 * @param arrivalTime    到着時刻
 * @param callingPortCount 寄港地の数
 * @param cargoTypeLabels 取り扱える貨物種別の表示名
 */
public record VoyageView(
        String voyageNumber,
        String vesselName,
        String carrierName,
        String origin,
        String originName,
        String destination,
        String destinationName,
        Instant departureTime,
        Instant arrivalTime,
        int callingPortCount,
        List<String> cargoTypeLabels) {

    public VoyageView {
        cargoTypeLabels = List.copyOf(cargoTypeLabels);
    }

    /** 直行便か。寄港地が無ければ直行である。 */
    public boolean isDirect() {
        return callingPortCount == 0;
    }
}
