package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.CancellationRequest;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.DischargeCandidate;
import java.util.List;

/** キャンセル API が返す形。 */
public final class CancellationResponses {

    private CancellationResponses() {
    }

    /**
     * キャンセル申請の 1 件。
     *
     * <p><strong>状態の読み方はサーバが返す。</strong>画面に対訳表を置くと、状態を
     * 足したときに画面が列挙の名前をそのまま出す。
     */
    public record CancellationResponse(Long cancellationId, String bookingId, String reason,
            String status, String statusLabel, String requestedBy, String requestedAt,
            String bookingStatusAtRequest, String bookingStatusAtRequestLabel,
            String dischargeLocationUnLocode, String dischargeLocationName, String decidedBy,
            String decidedAt, String decisionReason) {

        public static CancellationResponse from(CancellationRequest request, String bookingId,
                String dischargeLocationName) {
            return new CancellationResponse(request.id(), bookingId, request.reason(),
                    request.status().name(), request.status().label(), request.requestedBy(),
                    request.requestedAt().toString(),
                    request.bookingStatusAtRequest().name(),
                    BookingStatusLabels.of(request.bookingStatusAtRequest()),
                    request.dischargeLocation().orElse(null), dischargeLocationName,
                    request.decidedBy().orElse(null),
                    request.decidedAt().map(Object::toString).orElse(null),
                    request.decisionReason().orElse(null));
        }
    }

    /**
     * 承認待ちの 1 件と、その予約で選べる陸揚げ地（[ADR-025] 決定 4）。
     *
     * <p><strong>候補はサーバが作る。</strong>画面が旅程から組み立てると、候補の規則が
     * 2 か所に分かれる。
     */
    public record PendingCancellationResponse(Long cancellationId, String bookingId,
            String reason, String status, String statusLabel, String requestedBy,
            String requestedAt, String bookingStatusAtRequest,
            String bookingStatusAtRequestLabel, String dischargeLocationUnLocode,
            String dischargeLocationName, String decidedBy, String decidedAt,
            String decisionReason, List<DischargeCandidateResponse> dischargeCandidates) {

        public static PendingCancellationResponse from(CancellationRequest request, Cargo cargo) {
            CancellationResponse summary = CancellationResponse.from(request,
                    cargo.bookingId().map(Object::toString).orElse(null), null);
            return new PendingCancellationResponse(summary.cancellationId(), summary.bookingId(),
                    summary.reason(), summary.status(), summary.statusLabel(),
                    summary.requestedBy(), summary.requestedAt(),
                    summary.bookingStatusAtRequest(), summary.bookingStatusAtRequestLabel(),
                    summary.dischargeLocationUnLocode(), summary.dischargeLocationName(),
                    summary.decidedBy(), summary.decidedAt(), summary.decisionReason(),
                    cargo.dischargeCandidates().stream()
                            .map(DischargeCandidateResponse::from)
                            .toList());
        }
    }

    /** 陸揚げ地の候補。**なぜ候補なのかを添える**。 */
    public record DischargeCandidateResponse(String unLocode, String name, String reason) {

        static DischargeCandidateResponse from(DischargeCandidate candidate) {
            return new DischargeCandidateResponse(candidate.unLocode(), candidate.name(),
                    candidate.reason());
        }
    }

    /** 申請の結果（US30-2・US30-3）。**承認を待つかどうかをサーバが答える**。 */
    public record CancellationOutcomeResponse(CancellationResponse request,
            boolean awaitingApproval) {
    }
}
