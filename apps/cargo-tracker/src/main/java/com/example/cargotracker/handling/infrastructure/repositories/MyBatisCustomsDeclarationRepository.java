package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.handling.domain.model.CustomsDeclaration;
import com.example.cargotracker.handling.domain.model.CustomsStatus;
import com.example.cargotracker.handling.domain.model.CustomsStatusChange;
import com.example.cargotracker.handling.domain.model.DeclarationNumber;
import com.example.cargotracker.handling.domain.repository.CustomsDeclarationRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link CustomsDeclarationRepository} の MyBatis 実装（US29）。 */
@Repository
public class MyBatisCustomsDeclarationRepository implements CustomsDeclarationRepository {

    private final CustomsMapper mapper;

    public MyBatisCustomsDeclarationRepository(CustomsMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 申告と変更履歴を同じトランザクションで書く。
     *
     * <p><strong>履歴だけが落ちると「なぜ止めたのか」が消える。</strong>
     * 逆に申告だけが落ちると、履歴が実体の無い変更を語る。
     */
    @Override
    public boolean save(long handlingActivityId, CustomsDeclaration declaration) {
        CustomsDeclarationRecord row = toRecord(handlingActivityId, declaration);
        if (declaration.isNew()) {
            mapper.insert(row);
        } else {
            row.setId(declaration.id());
            if (mapper.update(row) != 1) {
                // **砦を置いたなら警報も要る。** 0 行のまま成功を返すと、
                // DB に書けていないのに荷主へ「通関しました」と通知が飛ぶ
                return false;
            }
        }
        for (CustomsStatusChange change : declaration.newChanges()) {
            CustomsHistoryRecord history = new CustomsHistoryRecord();
            history.setDeclarationId(row.getId());
            history.setStatusFrom(change.from().name());
            history.setStatusTo(change.to().name());
            history.setReason(change.reason());
            history.setChangedBy(change.changedBy());
            history.setChangedAt(change.changedAt());
            mapper.insertHistory(history);
        }
        declaration.changesSaved();
        return true;
    }

    @Override
    public Optional<CustomsDeclaration> findByTrackingNumber(String trackingNumber) {
        return Optional.ofNullable(mapper.findByTrackingNumber(trackingNumber))
                .map(MyBatisCustomsDeclarationRepository::toDomain);
    }

    @Override
    public Optional<CustomsDeclaration> findById(long declarationId) {
        return Optional.ofNullable(mapper.findById(declarationId))
                .map(MyBatisCustomsDeclarationRepository::toDomain);
    }

    @Override
    public Optional<Long> findCustomsHandlingId(String trackingNumber) {
        return Optional.ofNullable(mapper.findCustomsHandlingId(trackingNumber));
    }

    @Override
    public Optional<String> findTrackingNumber(long declarationId) {
        return Optional.ofNullable(mapper.findTrackingNumber(declarationId));
    }

    @Override
    public List<CustomsStatusChange> findHistory(long declarationId) {
        return mapper.findHistory(declarationId).stream()
                .map(row -> new CustomsStatusChange(
                        CustomsStatus.valueOf(row.getStatusFrom()),
                        CustomsStatus.valueOf(row.getStatusTo()),
                        row.getReason(), row.getChangedBy(), row.getChangedAt()))
                .toList();
    }

    private static CustomsDeclarationRecord toRecord(
            long handlingActivityId, CustomsDeclaration declaration) {
        CustomsDeclarationRecord row = new CustomsDeclarationRecord();
        row.setHandlingActivityId(handlingActivityId);
        row.setDeclarationNumber(declaration.declarationNumber().value());
        row.setDeclaredAt(declaration.declaredAt());
        row.setStatus(declaration.status().name());
        row.setClearedAt(declaration.clearedAt());
        row.setHeldSince(declaration.heldSince());
        return row;
    }

    private static CustomsDeclaration toDomain(CustomsDeclarationRecord row) {
        return CustomsDeclaration.reconstruct(
                row.getId(),
                new DeclarationNumber(row.getDeclarationNumber()),
                row.getDeclaredAt(),
                CustomsStatus.valueOf(row.getStatus()),
                row.getClearedAt(),
                row.getHeldSince());
    }
}
