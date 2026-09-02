package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.repository.CancellationRequestRepository;
import com.example.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.bookingms.domain.model.aggregates.CancellationRequest;
import com.example.bookingms.domain.model.valueobjects.CancellationStatus;
import java.util.List;
import java.util.Optional;

/** キャンセル申請の永続化。 */
public class MyBatisCancellationRequestRepository implements CancellationRequestRepository {

    private final CancellationRequestMapper mapper;

    public MyBatisCancellationRequestRepository(CancellationRequestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CancellationRequest save(CancellationRequest request) {
        CancellationRequestRecord row = toRecord(request);
        mapper.insert(row);
        return toDomain(mapper.findById(row.getId()));
    }

    @Override
    public CancellationRequest updateDecision(CancellationRequest request) {
        CancellationRequestRecord row = toRecord(request);
        row.setId(request.id());
        mapper.updateDecision(row);
        return toDomain(mapper.findById(request.id()));
    }

    @Override
    public Optional<CancellationRequest> findAwaitingByCargoId(Long cargoId) {
        return Optional.ofNullable(mapper.findAwaitingByCargoId(cargoId)).map(this::toDomain);
    }

    @Override
    public Optional<CancellationRequest> findLatestByCargoId(Long cargoId) {
        return Optional.ofNullable(mapper.findLatestByCargoId(cargoId)).map(this::toDomain);
    }

    @Override
    public List<CancellationRequest> findAllByCargoId(Long cargoId) {
        return mapper.findAllByCargoId(cargoId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<CancellationRequest> findAwaitingDecision(int limit) {
        return mapper.findAwaitingDecision(limit).stream().map(this::toDomain).toList();
    }

    @Override
    public List<CancellationRequest> findAwaitingDischarge(int limit) {
        return mapper.findAwaitingDischarge(limit).stream().map(this::toDomain).toList();
    }

    private static CancellationRequestRecord toRecord(CancellationRequest request) {
        CancellationRequestRecord row = new CancellationRequestRecord();
        row.setId(request.id());
        row.setCargoId(request.cargoId());
        row.setReason(request.reason());
        row.setStatus(request.status().name());
        row.setRequestedBy(request.requestedBy());
        row.setRequestedAt(request.requestedAt());
        row.setBookingStatusAtRequest(request.bookingStatusAtRequest().name());
        row.setDischargeLocationUnlocode(request.dischargeLocation().orElse(null));
        row.setDecidedBy(request.decidedBy().orElse(null));
        row.setDecidedAt(request.decidedAt().orElse(null));
        row.setDecisionReason(request.decisionReason().orElse(null));
        return row;
    }

    /** 復元では検査しない。列が無かったころの行が読めなくなる。 */
    private CancellationRequest toDomain(CancellationRequestRecord row) {
        return CancellationRequest.restore(row.getId(), row.getCargoId(), row.getReason(),
                CancellationStatus.restore(row.getStatus()), row.getRequestedBy(),
                row.getRequestedAt(),
                BookingStatus.valueOf(row.getBookingStatusAtRequest()),
                row.getDischargeLocationUnlocode(), row.getDecidedBy(), row.getDecidedAt(),
                row.getDecisionReason());
    }
}
