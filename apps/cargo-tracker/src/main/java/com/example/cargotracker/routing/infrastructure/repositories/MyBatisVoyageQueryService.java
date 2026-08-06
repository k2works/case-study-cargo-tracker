package com.example.cargotracker.routing.infrastructure.repositories;

import com.example.cargotracker.routing.application.internal.queryservices.VoyageQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageView;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.shared.application.paging.Page;
import com.example.cargotracker.shared.application.paging.PageRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link VoyageQueryService} の MyBatis 実装（読み取り専用アダプタ）。 */
@Service
public class MyBatisVoyageQueryService implements VoyageQueryService {

    private final VoyageQueryMapper mapper;
    private final ZoneId businessZone;

    public MyBatisVoyageQueryService(VoyageQueryMapper mapper, java.time.Clock clock) {
        this.mapper = mapper;
        // 検索条件の「日付」は利用者の暦の上の日である。**UTC で解釈すると、
        // 日本時間の朝に出る便が前日扱いになって検索から漏れる**
        this.businessZone = clock.getZone();
    }

    @Override
    public Page<VoyageView> search(
            String origin,
            String destination,
            LocalDate departureFrom,
            LocalDate departureTo,
            RoutingCargoType cargoType,
            PageRequest page) {

        String o = trim(origin);
        String d = trim(destination);
        // 下限はその日の 0 時、上限はその日の終わり。**上限をその日の 0 時にすると、
        // 指定した日に出る便がまるごと漏れる**
        Instant from = departureFrom == null
                ? null : departureFrom.atStartOfDay(businessZone).toInstant();
        Instant to = departureTo == null
                ? null : departureTo.atTime(LocalTime.MAX).atZone(businessZone).toInstant();
        String type = cargoType == null ? null : cargoType.name();

        long total = mapper.count(o, d, from, to, type);
        List<VoyageView> items = mapper.search(o, d, from, to, type, page.offset(), page.size())
                .stream()
                .map(MyBatisVoyageQueryService::toView)
                .toList();
        return Page.of(items, page, total);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(
                java.util.Locale.ROOT);
    }

    private static VoyageView toView(VoyageQueryRow row) {
        // 区間が n 本なら寄港地は n-1 箇所。区間が無い航海は登録できないが、
        // 読み取り側で 0 を下回らないようにしておく
        int callingPorts = Math.max(row.getMovementCount() - 1, 0);
        return new VoyageView(
                row.getVoyageNumber(),
                row.getVesselName(),
                row.getCarrierName(),
                row.getOrigin(),
                row.getOriginName(),
                row.getDestination(),
                row.getDestinationName(),
                row.getDepartureTime(),
                row.getArrivalTime(),
                callingPorts,
                MyBatisVoyageRepository.decodeCargoTypes(row.getCargoTypes()).stream()
                        .map(RoutingCargoType::displayName)
                        .toList());
    }
}
