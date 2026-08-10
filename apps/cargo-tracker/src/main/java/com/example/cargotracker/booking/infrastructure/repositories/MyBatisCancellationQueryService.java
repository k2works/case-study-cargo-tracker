package com.example.cargotracker.booking.infrastructure.repositories;

import com.example.cargotracker.booking.application.internal.commandservices
        .CancelBookingApprovalCommandService;
import com.example.cargotracker.booking.application.internal.queryservices.CancellationQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.CancellationView;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.CancellationRequest;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.repository.CancellationRequestRepository;
import com.example.cargotracker.shared.domain.model.Location;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link CancellationQueryService} の実装（US30）。
 *
 * <p><strong>陸揚げ地の候補は集約と同じ道で作る</strong>（ADR-021 の T1）。
 * 画面用に別の計算を書くと、見えている選択肢と承認が受け付ける値がずれる。
 *
 * <p><strong>一覧では候補を組み立てない。</strong> 1 行ごとに現在地を問い合わせると、
 * 行数に比例して問い合わせが増える（C4）。候補が要るのは承認の画面だけである。
 */
@Service
public class MyBatisCancellationQueryService implements CancellationQueryService {

    private final CancellationRequestRepository repository;
    private final CancelBookingApprovalCommandService approvalService;
    private final BookingQueryMapper bookingMapper;

    public MyBatisCancellationQueryService(
            CancellationRequestRepository repository,
            CancelBookingApprovalCommandService approvalService,
            BookingQueryMapper bookingMapper) {
        this.repository = repository;
        this.approvalService = approvalService;
        this.bookingMapper = bookingMapper;
    }

    @Override
    public List<CancellationView> findPending() {
        return repository.findPending().stream()
                .map(request -> toView(request, null))
                .toList();
    }

    @Override
    public Optional<CancellationView> findById(long id) {
        return repository.findById(id).map(request -> {
            // **候補は承認の画面でだけ組み立てる**（一覧では要らない）
            Cargo cargo = approvalService
                    .findCargo(request.bookingId().value().toString())
                    .orElse(null);
            return toView(request, cargo);
        });
    }

    @Override
    public List<CancellationView> findByBookingId(String bookingId) {
        if (bookingId == null || bookingId.isBlank()) {
            return List.of();
        }
        UUID id;
        try {
            id = UUID.fromString(bookingId.strip());
        } catch (IllegalArgumentException e) {
            // **形式の違う ID を例外にしない。** 予約詳細が 500 になる
            return List.of();
        }
        return repository.findByBookingId(new BookingId(id)).stream()
                .map(request -> toView(request, null))
                .toList();
    }

    @Override
    public int countPending() {
        return repository.countPending();
    }

    private CancellationView toView(CancellationRequest request, Cargo cargo) {
        BookingQueryRow row = bookingMapper.findByBookingId(request.bookingId().value());
        List<Location> candidates = cargo == null
                ? List.of() : approvalService.candidatesFor(cargo);
        Location discharge = request.dischargeLocation();
        return new CancellationView(
                request.id(),
                request.bookingId().value().toString(),
                row == null ? null : row.getTrackingNumber(),
                row == null ? null : row.getShipperName(),
                row == null ? null : row.getOrigin(),
                row == null ? null : row.getDestination(),
                request.reason(),
                request.requestedBy(),
                request.requestedAt(),
                request.status().displayName(),
                request.status().badgeClass(),
                request.isPending(),
                request.feeRate().asPercent(),
                candidates.isEmpty() ? null : candidates.get(0).unlocode(),
                candidates.stream().map(Location::unlocode).toList(),
                discharge == null ? null : discharge.unlocode(),
                request.decision().by(),
                request.decision().at(),
                request.decision().reason());
    }
}
