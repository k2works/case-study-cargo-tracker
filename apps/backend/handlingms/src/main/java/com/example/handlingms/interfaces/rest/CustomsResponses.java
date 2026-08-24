package com.example.handlingms.interfaces.rest;

import com.example.handlingms.domain.model.CustomsDeclaration;
import com.example.handlingms.domain.model.CustomsStatus;
import com.example.handlingms.domain.model.CustomsStatusChange;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 通関申告 API が返す形。 */
public final class CustomsResponses {

    private CustomsResponses() {
    }

    /**
     * 申告の 1 件。
     *
     * <p><strong>状態の読み方はサーバが返す</strong>（[ADR-023] 決定 1 と同じ形）。
     * 画面に対訳表を置くと、状態を足したときに画面が列挙の名前をそのまま出す。
     *
     * <p><strong>留置の経過もサーバが数える。</strong>画面で日付を引き算すると、
     * 利用者の端末の時計と時差の分だけ結果が変わる。
     */
    public record CustomsDeclarationResponse(Long declarationId, String declarationNumber,
            String bookingId, String trackingNumber, String declaredAt, String status,
            String statusLabel, String clearedAt, boolean heldOverdue, Long heldDays,
            String remarks) {

        static CustomsDeclarationResponse from(CustomsDeclaration declaration, LocalDate today,
                ZoneId zone, int thresholdDays) {
            return new CustomsDeclarationResponse(
                    declaration.id(),
                    declaration.declarationNumber().value(),
                    declaration.cargoBookingId().value(),
                    declaration.trackingNumber().value(),
                    display(declaration.declaredAt(), zone),
                    declaration.status().name(),
                    declaration.status().label(),
                    declaration.clearedAt().map(at -> display(at, zone)).orElse(null),
                    declaration.isHeldOverdue(today, zone, thresholdDays),
                    declaration.heldDays(today, zone).orElse(null),
                    declaration.remarks().orElse(null));
        }
    }

    /** 詳細。状態変更の履歴を伴う（US29-8）。 */
    public record CustomsDeclarationDetailResponse(Long declarationId, String declarationNumber,
            String bookingId, String trackingNumber, String declaredAt, String status,
            String statusLabel, String clearedAt, boolean heldOverdue, Long heldDays,
            String remarks, List<CustomsStatusChangeResponse> history) {

        static CustomsDeclarationDetailResponse from(CustomsDeclaration declaration,
                LocalDate today, ZoneId zone, int thresholdDays) {
            CustomsDeclarationResponse summary =
                    CustomsDeclarationResponse.from(declaration, today, zone, thresholdDays);
            return new CustomsDeclarationDetailResponse(summary.declarationId(),
                    summary.declarationNumber(), summary.bookingId(), summary.trackingNumber(),
                    summary.declaredAt(), summary.status(), summary.statusLabel(),
                    summary.clearedAt(), summary.heldOverdue(), summary.heldDays(),
                    summary.remarks(),
                    declaration.history().stream()
                            .map(change -> CustomsStatusChangeResponse.from(change, zone))
                            .toList());
        }
    }

    /** 状態変更 1 件。**理由は必ず値を持つ**（US29-2）。 */
    public record CustomsStatusChangeResponse(String fromStatus, String fromStatusLabel,
            String toStatus, String toStatusLabel, String changedBy, String changedAt,
            String reason) {

        static CustomsStatusChangeResponse from(CustomsStatusChange change, ZoneId zone) {
            return new CustomsStatusChangeResponse(change.fromStatus().name(),
                    change.fromStatus().label(), change.toStatus().name(),
                    change.toStatus().label(), change.changedBy(),
                    display(change.changedAt(), zone), change.reason());
        }
    }

    /** 通関状態の選択肢。**画面が一覧を持たない**。 */
    public record CustomsStatusResponse(String status, String label) {

        static CustomsStatusResponse from(CustomsStatus status) {
            return new CustomsStatusResponse(status.name(), status.label());
        }
    }

    /** 留置 3 日超の件数（US29-6）。**件数から対象一覧へ辿れる**。 */
    public record OverdueCustomsSummary(long count) {
    }

    /** 画面に出す日時。**業務の暦で整形する**——UTC の生の値を荷役の担当者に見せない。 */
    static String display(java.time.Instant at, ZoneId zone) {
        return at == null ? null
                : java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .format(at.atZone(zone));
    }
}
