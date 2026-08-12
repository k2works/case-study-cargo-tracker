package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.handling.domain.model.aggregates.CorrectionRequest;
import com.example.cargotracker.handling.domain.model.valueobjects.CorrectionRequestType;
import com.example.cargotracker.handling.domain.model.valueobjects.CorrectionStatus;
import com.example.cargotracker.handling.domain.repository.CorrectionRequestRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link CorrectionRequestRepository} の MyBatis 実装（US36）。 */
@Repository
public class MyBatisCorrectionRequestRepository implements CorrectionRequestRepository {

    private final CorrectionMapper mapper;

    public MyBatisCorrectionRequestRepository(CorrectionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long save(CorrectionRequest request) {
        CorrectionRecord row = toRecord(request);
        mapper.insert(row);
        return row.getId();
    }

    @Override
    public boolean update(CorrectionRequest request) {
        return mapper.update(toRecord(request)) == 1;
    }

    @Override
    public Optional<CorrectionRequest> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(MyBatisCorrectionRequestRepository::toDomain);
    }

    @Override
    public List<CorrectionRequest> findPending() {
        return mapper.findPending().stream()
                .map(MyBatisCorrectionRequestRepository::toDomain).toList();
    }

    @Override
    public List<CorrectionRequest> findByBookingId(java.util.UUID bookingId) {
        return mapper.findByBookingId(bookingId).stream()
                .map(MyBatisCorrectionRequestRepository::toDomain).toList();
    }

    @Override
    public List<CorrectionRequest> findByHandlingActivityId(long handlingActivityId) {
        return mapper.findByHandlingActivityId(handlingActivityId).stream()
                .map(MyBatisCorrectionRequestRepository::toDomain).toList();
    }

    @Override
    public int countPending() {
        return mapper.countPending();
    }

    private static CorrectionRecord toRecord(CorrectionRequest request) {
        CorrectionRecord row = new CorrectionRecord();
        row.setId(request.id());
        row.setHandlingActivityId(request.handlingActivityId());
        row.setRequestType(request.type().name());
        row.setReason(request.reason());
        row.setCorrectedCompletionTime(request.details().correctedCompletionTime());
        row.setCorrectedNote(request.details().correctedNote());
        row.setRequestedBy(request.requestedBy());
        row.setRequestedAt(request.requestedAt());
        row.setStatus(request.status().name());
        row.setDecidedBy(request.decision().by());
        row.setDecidedAt(request.decision().at());
        row.setDecisionReason(request.decision().reason());
        row.setVersion(request.version());
        return row;
    }

    /** <strong>復元では検査しない。</strong> 列が無かったころの行も読み戻せる。 */
    private static CorrectionRequest toDomain(CorrectionRecord row) {
        return CorrectionRequest.reconstruct(
                row.getId(),
                row.getHandlingActivityId(),
                new CorrectionRequest.Details(
                        CorrectionRequestType.valueOf(row.getRequestType()),
                        row.getReason(),
                        row.getCorrectedCompletionTime(),
                        row.getCorrectedNote()),
                new CorrectionRequest.Requester(row.getRequestedBy(), row.getRequestedAt()),
                new CorrectionRequest.Decision(
                        CorrectionStatus.valueOf(row.getStatus()),
                        row.getDecidedBy(), row.getDecidedAt(), row.getDecisionReason()),
                row.getVersion());
    }

    @Override
    public List<java.util.UUID> findBookingIdsWithPendingCorrection(
            java.util.Collection<java.util.UUID> bookingIds) {
        return mapper.findBookingIdsWithPendingCorrection(bookingIds);
    }
}
