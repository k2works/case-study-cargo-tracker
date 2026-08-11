package com.example.cargotracker.handling.infrastructure.repositories;

import com.example.cargotracker.handling.application.internal.outboundservices.acl.ApprovedCancellations;
import com.example.cargotracker.handling.application.internal.queryservices.DischargeOrderView;
import com.example.cargotracker.handling.application.internal.queryservices.HandlingActivityView;
import com.example.cargotracker.handling.application.internal.queryservices.HandlingQueryService;
import com.example.cargotracker.handling.domain.model.HandlingType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@link HandlingQueryService} の MyBatis 実装（CQRS の読み取り側）。 */
@Service
public class MyBatisHandlingQueryService implements HandlingQueryService {

    private final HandlingMapper mapper;
    private final ApprovedCancellations approvedCancellations;

    public MyBatisHandlingQueryService(
            HandlingMapper mapper, ApprovedCancellations approvedCancellations) {
        this.mapper = mapper;
        this.approvedCancellations = approvedCancellations;
    }

    @Override
    public List<HandlingActivityView> findRecent(int limit) {
        return mapper.findRecent(limit).stream()
                .map(MyBatisHandlingQueryService::toView)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>問い合わせは 2 回で済ませる。</strong> 手配をまとめて受け取り、
     * 済んだ予約もまとめて引く。1 件ごとに「降ろしたか」を尋ねると、
     * <strong>待ち行列が伸びるほど遅くなる</strong>（IT13 の C4 / IT15 の P3）。
     */
    @Override
    public List<DischargeOrderView> findPendingDischarges() {
        List<ApprovedCancellations.DischargeOrder> orders =
                approvedCancellations.findApprovedDischarges();
        if (orders.isEmpty()) {
            // **空で問い合わせない。** IN () は SQL として成り立たない
            return List.of();
        }
        Set<UUID> unloaded = new HashSet<>(mapper.findUnloadedBookingIds(
                orders.stream().map(o -> UUID.fromString(o.bookingId())).toList()));
        return orders.stream()
                .filter(o -> !unloaded.contains(UUID.fromString(o.bookingId())))
                .map(o -> new DischargeOrderView(
                        o.trackingNumber(), o.dischargeUnlocode(),
                        o.dischargeName(), o.decidedAt()))
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
                row.getOperatorName() == null ? "" : row.getOperatorName(),
                specialHandlingLabel(row.getCargoType()),
                row.getId(),
                // **取り消しで戻る状態を持つのは引取だけである**（US36）
                HandlingType.CLAIM.name().equals(row.getEventType()),
                row.getCancelledAt() != null);
    }

    /**
     * 特別な取り扱いの表示名（US05）。
     *
     * <p><strong>一般貨物では空にする。</strong> すべての行にバッジが付くと、
     * 気をつけるべき行が埋もれる。
     */
    private static String specialHandlingLabel(String cargoType) {
        if (cargoType == null) {
            return "";
        }
        return switch (cargoType) {
            case "HAZARDOUS" -> "危険物";
            case "REFRIGERATED" -> "冷凍・冷蔵";
            default -> "";
        };
    }
}
