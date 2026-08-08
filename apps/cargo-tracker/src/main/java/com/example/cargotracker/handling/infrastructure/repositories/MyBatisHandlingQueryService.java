package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.handling.application.internal.queryservices.HandlingActivityView;
import com.example.cargotracker.handling.application.internal.queryservices.HandlingQueryService;
import com.example.cargotracker.handling.domain.model.HandlingType;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link HandlingQueryService} の MyBatis 実装（CQRS の読み取り側）。 */
@Service
public class MyBatisHandlingQueryService implements HandlingQueryService {

    private final HandlingMapper mapper;

    public MyBatisHandlingQueryService(HandlingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<HandlingActivityView> findRecent(int limit) {
        return mapper.findRecent(limit).stream()
                .map(MyBatisHandlingQueryService::toView)
                .toList();
    }

    private static HandlingActivityView toView(HandlingActivityRecord row) {
        return new HandlingActivityView(
                row.getEventCompletionTime(),
                // **日本語ラベルの正典は列挙型が持つ。** 画面に対応表を書き写さない
                HandlingType.valueOf(row.getEventType()).displayName(),
                row.getLocationUnlocode(),
                row.getVoyageNumber() == null ? "" : row.getVoyageNumber(),
                // IT6 以前の記録は番号を持たない（V13 で追加した列）
                row.getTrackingNumber() == null ? "" : row.getTrackingNumber(),
                row.getBookingId().toString(),
                // **引き渡しの証明は残すだけでなく読めなければ意味がない**（レビュー H3）
                row.getClaimConsigneeName() == null ? "" : row.getClaimConsigneeName(),
                row.getNote() == null ? "" : row.getNote(),
                row.getOperatorName() == null ? "" : row.getOperatorName());
    }
}
