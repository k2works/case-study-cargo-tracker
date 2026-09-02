package com.example.bookingms.interfaces.rest;

/** キャンセル API が受け取る形。 */
public final class CancellationRequests {

    private CancellationRequests() {
    }

    /** 申請（US30-1）。**理由は必須**であり、エンティティが断る。 */
    public record RequestCancellationRequest(String reason) {
    }

    /** 承認（US30-5）。**陸揚げ地は必須**で、候補に限る（[ADR-025] 決定 4）。 */
    public record ApproveCancellationRequest(String dischargeLocationUnLocode,
            String decisionReason) {
    }

    /** 却下（US30-7）。**理由は必須**。予約は輸送中のまま維持される。 */
    public record RejectCancellationRequest(String decisionReason) {
    }
}
