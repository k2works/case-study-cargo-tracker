package com.example.handlingms.infrastructure.repositories;

import com.example.handlingms.application.port.CustomsDeclarationRepository;
import com.example.handlingms.domain.model.CargoBookingId;
import com.example.handlingms.domain.model.CustomsDeclaration;
import com.example.handlingms.domain.model.CustomsStatus;
import com.example.handlingms.domain.model.CustomsStatusChange;
import com.example.handlingms.domain.model.DeclarationNumber;
import com.example.handlingms.domain.model.HandlingTrackingNumber;
import java.util.List;
import java.util.Optional;

/**
 * 通関申告の永続化。
 *
 * <p><strong>履歴は状態と同じ呼び出しで書く。</strong>別々になると、状態は変わったのに
 * 履歴に出ない行ができ、監査で「誰が変えたか」が読めない。
 */
public class MyBatisCustomsDeclarationRepository implements CustomsDeclarationRepository {

    private final CustomsDeclarationMapper declarations;
    private final CustomsStatusHistoryMapper histories;

    public MyBatisCustomsDeclarationRepository(CustomsDeclarationMapper declarations,
            CustomsStatusHistoryMapper histories) {
        this.declarations = declarations;
        this.histories = histories;
    }

    @Override
    public CustomsDeclaration save(CustomsDeclaration declaration) {
        CustomsDeclarationRecord row = toRecord(declaration);
        declarations.insert(row);
        // 登録そのものも履歴の 1 行目として残す（from_status も NOT NULL）
        declaration.history().forEach(change -> insertHistory(row.getId(), change));
        return findById(row.getId()).orElseThrow();
    }

    @Override
    public CustomsDeclaration updateStatus(CustomsDeclaration declaration) {
        CustomsDeclarationRecord row = toRecord(declaration);
        declarations.updateStatus(row);
        // **積むのは最後の 1 件だけ。**すべて書き直すと、履歴が申告のたびに倍になる
        insertHistory(declaration.id(), declaration.history().getLast());
        return findById(declaration.id()).orElseThrow();
    }

    @Override
    public Optional<CustomsDeclaration> findById(long declarationId) {
        return Optional.ofNullable(declarations.findById(declarationId)).map(this::toDomain);
    }

    @Override
    public Optional<CustomsDeclaration> findUnsettledByTrackingNumber(
            HandlingTrackingNumber trackingNumber) {
        return Optional
                .ofNullable(declarations.findUnsettledByTrackingNumber(trackingNumber.value()))
                .map(this::toDomain);
    }

    @Override
    public Optional<CustomsDeclaration> findLatestByBookingId(CargoBookingId cargoBookingId) {
        return Optional.ofNullable(declarations.findLatestByBookingId(cargoBookingId.value()))
                .map(this::toDomain);
    }

    @Override
    public List<CustomsDeclaration> search(String bookingId, String trackingNumber,
            CustomsStatus status, boolean unsettledOnly, int limit) {
        return declarations
                .search(blankToNull(bookingId), blankToNull(trackingNumber),
                        status == null ? null : status.name(), unsettledOnly, limit)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long count(String bookingId, String trackingNumber, CustomsStatus status,
            boolean unsettledOnly) {
        return declarations.count(blankToNull(bookingId), blankToNull(trackingNumber),
                status == null ? null : status.name(), unsettledOnly);
    }

    private void insertHistory(Long declarationId, CustomsStatusChange change) {
        CustomsStatusHistoryRecord row = new CustomsStatusHistoryRecord();
        row.setCustomsDeclarationId(declarationId);
        row.setFromStatus(change.fromStatus().name());
        row.setToStatus(change.toStatus().name());
        row.setChangedBy(change.changedBy());
        row.setChangedAt(change.changedAt());
        row.setReason(change.reason());
        histories.insert(row);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static CustomsDeclarationRecord toRecord(CustomsDeclaration declaration) {
        CustomsDeclarationRecord row = new CustomsDeclarationRecord();
        row.setId(declaration.id());
        row.setDeclarationNumber(declaration.declarationNumber().value());
        row.setBookingId(declaration.cargoBookingId().value());
        row.setTrackingNumber(declaration.trackingNumber().value());
        row.setDeclaredAt(declaration.declaredAt());
        row.setStatus(declaration.status().name());
        row.setClearedAt(declaration.clearedAt().orElse(null));
        row.setRemarks(declaration.remarks().orElse(null));
        return row;
    }

    /** 復元では検査しない。列が無かったころの行が読めなくなる。 */
    private CustomsDeclaration toDomain(CustomsDeclarationRecord row) {
        List<CustomsStatusChange> history = histories.findByDeclarationId(row.getId()).stream()
                .map(change -> new CustomsStatusChange(
                        CustomsStatus.restore(change.getFromStatus()),
                        CustomsStatus.restore(change.getToStatus()),
                        change.getChangedBy(), change.getChangedAt(), change.getReason()))
                .toList();

        return CustomsDeclaration.restore(row.getId(),
                new DeclarationNumber(row.getDeclarationNumber()),
                CargoBookingId.of(row.getBookingId()),
                HandlingTrackingNumber.of(row.getTrackingNumber()),
                row.getDeclaredAt(), CustomsStatus.restore(row.getStatus()), row.getClearedAt(),
                row.getRemarks(), history);
    }
}
