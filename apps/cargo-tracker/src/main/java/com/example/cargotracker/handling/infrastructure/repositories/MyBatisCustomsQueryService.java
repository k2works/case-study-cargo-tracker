package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.handling.application.internal.queryservices.CustomsDeclarationView;
import com.example.cargotracker.handling.application.internal.queryservices.CustomsQueryService;
import com.example.cargotracker.handling.domain.model.CustomsDeclaration;
import com.example.cargotracker.handling.domain.model.CustomsStatus;
import com.example.cargotracker.handling.domain.model.CustomsStatusChange;
import com.example.cargotracker.handling.domain.repository.CustomsDeclarationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** {@link CustomsQueryService} の MyBatis 実装（US29）。 */
@Service
public class MyBatisCustomsQueryService implements CustomsQueryService {

    private final CustomsListMapper mapper;
    private final CustomsDeclarationRepository declarationRepository;

    /** **業務のタイムゾーンで「今日」を決める。** UTC で判断すると時差の分だけずれる。 */
    private final Clock clock;

    public MyBatisCustomsQueryService(
            CustomsListMapper mapper,
            CustomsDeclarationRepository declarationRepository,
            Clock clock) {
        this.mapper = mapper;
        this.declarationRepository = declarationRepository;
        this.clock = clock;
    }

    @Override
    public List<CustomsDeclarationView> search(String keyword, String status) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String statusFilter = status == null || status.isBlank() ? null : status;
        return mapper.search(normalized, statusFilter).stream().map(this::toView).toList();
    }

    /**
     * 留置日数で絞る（C33）。
     *
     * <p><strong>日数の判定はドメインに任せる。</strong> SQL に日数を書くと、
     * 規則が集約と SQL の 2 か所に散る（{@code countHeldTooLong} と同じ判断）。
     */
    @Override
    public List<CustomsDeclarationView> search(String keyword, String status, Integer heldDays) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String statusFilter = status == null || status.isBlank() ? null : status;
        return mapper.search(normalized, statusFilter).stream()
                // **しきい値は集約が持つ**（CustomsDeclaration.HELD_WARNING_DAYS）。
                // 画面から任意の日数を受け取る形にすると、ダッシュボードの件数と
                // 一覧の中身が別の規則で決まることになる
                .filter(row -> heldDays == null || heldTooLong(row))
                .map(this::toView)
                .toList();
    }

    @Override
    public Optional<CustomsDeclarationView> findById(long declarationId) {
        return Optional.ofNullable(mapper.findById(declarationId)).map(this::toView);
    }

    @Override
    public List<CustomsStatusChange> findHistory(long declarationId) {
        return declarationRepository.findHistory(declarationId);
    }

    /**
     * 留置が長引いている件数。
     *
     * <p><strong>日数の判定はドメインに任せる。</strong> SQL に「3 日」を書くと、
     * 規則が集約と SQL の 2 か所に散る。件数は多くならない（留置は例外的な状態である）。
     */
    @Override
    public int countHeldTooLong() {
        // **カードの件数と一覧の中身は同じ経路で決める**（C33）
        return search(null, CustomsStatus.HELD.name(), CustomsDeclaration.HELD_WARNING_DAYS)
                .size();
    }

    private boolean heldTooLong(CustomsListRow row) {
        return CustomsDeclaration.reconstruct(
                        row.getId(), null, row.getDeclaredAt(),
                        CustomsStatus.valueOf(row.getStatus()),
                        row.getClearedAt(), row.getHeldSince())
                .heldTooLong(LocalDate.now(clock), clock.getZone());
    }

    private CustomsDeclarationView toView(CustomsListRow row) {
        CustomsStatus status = CustomsStatus.valueOf(row.getStatus());
        return new CustomsDeclarationView(
                row.getId(),
                row.getDeclarationNumber(),
                row.getTrackingNumber(),
                row.getBookingId(),
                status,
                status.displayName(),
                badgeOf(status),
                row.getDeclaredAt(),
                row.getClearedAt(),
                row.getHeldSince(),
                heldTooLong(row),
                row.getShipperName() == null ? "" : row.getShipperName());
    }

    /** 状態のバッジ。**放置するとコストが発生する状態を目立たせる。** */
    private static String badgeOf(CustomsStatus status) {
        return switch (status) {
            case CLEARED -> "bg-success";
            case HELD -> "bg-warning text-dark";
            case REJECTED -> "bg-danger";
            case PENDING -> "bg-secondary";
        };
    }
}
