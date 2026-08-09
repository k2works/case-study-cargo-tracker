package com.example.cargotracker.tracking.infrastructure.repositories;

import com.example.cargotracker.tracking.application.internal.outboundservices.acl.CargoContacts;
import com.example.cargotracker.tracking.application.internal.queryservices
        .TrackingExceptionQueryService;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingExceptionView;
import com.example.cargotracker.tracking.domain.model.ExceptionType;
import com.example.cargotracker.tracking.domain.model.TransportStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@link TrackingExceptionQueryService} の MyBatis 実装。 */
@Service
public class MyBatisTrackingExceptionQueryService implements TrackingExceptionQueryService {

    private final TrackingMapper mapper;
    private final CargoContacts cargoContacts;

    public MyBatisTrackingExceptionQueryService(
            TrackingMapper mapper, CargoContacts cargoContacts) {
        this.mapper = mapper;
        this.cargoContacts = cargoContacts;
    }

    @Override
    public List<TrackingExceptionView> search(boolean unresolvedOnly, boolean escalatedOnly) {
        List<TrackingExceptionListRow> rows = mapper.search(unresolvedOnly, escalatedOnly);
        // **荷主名はまとめて引く**（N+1 を作らない）
        Map<UUID, String> names = cargoContacts.findShipperNames(
                rows.stream().map(row -> UUID.fromString(row.getBookingId())).toList());
        return rows.stream().map(row -> toView(row, names)).toList();
    }

    @Override
    public Optional<TrackingExceptionView> findById(long exceptionId) {
        TrackingExceptionListRow row = mapper.findExceptionById(exceptionId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toView(row, cargoContacts.findShipperNames(
                List.of(UUID.fromString(row.getBookingId())))));
    }

    @Override
    public int countUnresolved(boolean escalatedOnly) {
        return mapper.countUnresolved(escalatedOnly);
    }

    private static TrackingExceptionView toView(
            TrackingExceptionListRow row, Map<UUID, String> names) {
        return new TrackingExceptionView(
                row.getId(),
                row.getTrackingNumber(),
                row.getBookingId(),
                ExceptionType.valueOf(row.getExceptionType()).displayName(),
                row.getLocationUnlocode(),
                row.getOccurredAt(),
                row.getDescription(),
                row.isEscalationFlag(),
                TransportStatus.valueOf(row.getStatusBefore()).displayName(),
                row.getResolvedAt(),
                row.getResolutionNotes(),
                row.getRevisedArrival(),
                // **荷主が引けなくても行は出す。** 連絡先が分からないことより、
                // 例外そのものが一覧から消えるほうが危うい
                names.getOrDefault(UUID.fromString(row.getBookingId()), ""));
    }
}
