package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CancellationFeeRate;
import com.example.cargotracker.booking.domain.model.aggregates.CancellationRequest;
import com.example.cargotracker.booking.domain.model.valueobjects.CancellationStatus;
import com.example.cargotracker.booking.domain.repository.CancellationRequestRepository;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link CancellationRequestRepository} の MyBatis 実装（US30）。 */
@Repository
public class MyBatisCancellationRequestRepository implements CancellationRequestRepository {

    private final CancellationMapper mapper;

    public MyBatisCancellationRequestRepository(CancellationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long save(CancellationRequest request) {
        CancellationRecord row = toRecord(request);
        mapper.insert(row);
        return row.getId();
    }

    @Override
    public boolean update(CancellationRequest request) {
        return mapper.update(toRecord(request)) == 1;
    }

    @Override
    public Optional<CancellationRequest> findById(long id) {
        return Optional.ofNullable(mapper.findById(id))
                .map(MyBatisCancellationRequestRepository::toDomain);
    }

    @Override
    public List<CancellationRequest> findPending() {
        return mapper.findPending().stream()
                .map(MyBatisCancellationRequestRepository::toDomain)
                .toList();
    }

    @Override
    public int countPending() {
        return mapper.countPending();
    }

    @Override
    public List<CancellationRequest> findByBookingId(BookingId bookingId) {
        return mapper.findByBookingId(bookingId.value()).stream()
                .map(MyBatisCancellationRequestRepository::toDomain)
                .toList();
    }

    @Override
    public boolean existsPendingFor(BookingId bookingId) {
        return mapper.countPendingFor(bookingId.value()) > 0;
    }

    private static CancellationRecord toRecord(CancellationRequest request) {
        CancellationRecord row = new CancellationRecord();
        row.setId(request.id());
        row.setBookingId(request.bookingId().value());
        row.setReason(request.reason());
        row.setRequestedBy(request.requestedBy());
        row.setRequestedAt(request.requestedAt());
        row.setStatus(request.status().name());
        row.setFeeRate(request.feeRate().value());
        Location discharge = request.dischargeLocation();
        row.setDischargeLocationUnlocode(discharge == null ? null : discharge.unlocode());
        row.setDecidedBy(request.decision().by());
        row.setDecidedAt(request.decision().at());
        row.setDecisionReason(request.decision().reason());
        row.setVersion(request.version());
        return row;
    }

    /**
     * 永続化された行から復元する。
     *
     * <p><strong>ここでは検査しない。</strong> 新しい不変条件で既存の行を
     * 読めなくしない（V22 / V26 / V32 と同じ判断）。
     */
    private static CancellationRequest toDomain(CancellationRecord row) {
        return CancellationRequest.reconstruct(
                row.getId(),
                new BookingId(row.getBookingId()),
                row.getReason(),
                new CancellationRequest.Requester(row.getRequestedBy(), row.getRequestedAt()),
                new CancellationFeeRate(row.getFeeRate()),
                new CancellationRequest.Decision(
                        CancellationStatus.valueOf(row.getStatus()),
                        row.getDecidedBy(), row.getDecidedAt(), row.getDecisionReason(),
                        row.getDischargeLocationUnlocode() == null
                                ? null : Location.of(row.getDischargeLocationUnlocode())),
                row.getVersion());
    }
}
