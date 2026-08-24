package com.example.bookingms.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * キャンセル申請（US30・UC22）。
 *
 * <p><strong>輸送中の貨物は、どこで降ろすかを決めないとキャンセルできない。</strong>
 * だから追跡管理者の承認を経る。輸送開始前は申請と同時に確定する。
 *
 * <p><strong>申請時点の予約状態を持つ。</strong>キャンセル料の料率はこれで決まる
 * （US23・IT11）。あとから予約の状態を見ても、そのときどこまで進んでいたかは分からない。
 *
 * <p><strong>却下しても予約は輸送中のまま。</strong>却下は「キャンセルしない」という
 * 決定であり、予約を止める決定ではない。止めてしまうと、貨物は行き先を失ったまま
 * 船に乗り続ける。
 */
public final class CancellationRequest {

    private final Long id;
    private final Long cargoId;
    private final String reason;
    private final CancellationStatus status;
    private final String requestedBy;
    private final Instant requestedAt;
    private final BookingStatus bookingStatusAtRequest;
    private final String dischargeLocationUnLocode;
    private final String decidedBy;
    private final Instant decidedAt;
    private final String decisionReason;

    @SuppressWarnings("java:S107")
    private CancellationRequest(Long id, Long cargoId, String reason, CancellationStatus status,
            String requestedBy, Instant requestedAt, BookingStatus bookingStatusAtRequest,
            String dischargeLocationUnLocode, String decidedBy, Instant decidedAt,
            String decisionReason) {
        this.id = id;
        this.cargoId = cargoId;
        this.reason = reason;
        this.status = status;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.bookingStatusAtRequest = bookingStatusAtRequest;
        this.dischargeLocationUnLocode = dischargeLocationUnLocode;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.decisionReason = decisionReason;
    }

    /**
     * 申請する（US30-1）。
     *
     * <p><strong>理由は必須である。</strong>あとから「なぜ止めたのか」を読むのは、
     * 承認する追跡管理者と、荷主に説明する担当者である。
     *
     * @param inTransit 輸送中か。<strong>輸送開始前は承認を待たずに確定する</strong>（US30-2）
     */
    public static CancellationRequest request(Long cargoId, String reason, String requestedBy,
            Instant requestedAt, BookingStatus bookingStatusAtRequest, boolean inTransit) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("キャンセルの理由を入力してください");
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("申請者は必須です");
        }
        if (requestedAt == null || bookingStatusAtRequest == null) {
            throw new IllegalArgumentException("申請日時と申請時点の予約状態は必須です");
        }
        return inTransit
                ? new CancellationRequest(null, cargoId, reason, CancellationStatus.REQUESTED,
                        requestedBy, requestedAt, bookingStatusAtRequest, null, null, null, null)
                // 輸送開始前は承認を待つ理由が無い。貨物はまだ動いていない
                : new CancellationRequest(null, cargoId, reason, CancellationStatus.APPROVED,
                        requestedBy, requestedAt, bookingStatusAtRequest, null, requestedBy,
                        requestedAt, "輸送開始前のため即時に確定しました");
    }

    /** 永続化された行から復元する。<strong>ここでは検査しない</strong>（既存行を壊さない）。 */
    @SuppressWarnings("java:S107")
    public static CancellationRequest restore(Long id, Long cargoId, String reason,
            CancellationStatus status, String requestedBy, Instant requestedAt,
            BookingStatus bookingStatusAtRequest, String dischargeLocationUnLocode,
            String decidedBy, Instant decidedAt, String decisionReason) {
        return new CancellationRequest(id, cargoId, reason, status, requestedBy, requestedAt,
                bookingStatusAtRequest, dischargeLocationUnLocode, decidedBy, decidedAt,
                decisionReason);
    }

    /**
     * 承認する（US30-5）。
     *
     * <p><strong>陸揚げ地は必須である。</strong>どこで降ろすかを決めないと、承認しても
     * 現場は動けない。<strong>候補に入っているかの判定は {@link Cargo} が持つ</strong>——
     * ここで旅程を見に行くと、候補の規則が 2 か所に分かれる。
     *
     * @throws IllegalStateException 判断を待っていないとき
     * @throws IllegalArgumentException 陸揚げ地が空のとき
     */
    public CancellationRequest approve(String dischargeLocationUnLocode, String decidedBy,
            String decisionReason, Instant decidedAt) {
        requireAwaitingDecision();
        if (dischargeLocationUnLocode == null || dischargeLocationUnLocode.isBlank()) {
            throw new IllegalArgumentException("陸揚げ地を指定してください");
        }
        return new CancellationRequest(id, cargoId, reason, CancellationStatus.APPROVED,
                requestedBy, requestedAt, bookingStatusAtRequest, dischargeLocationUnLocode,
                requireDecider(decidedBy), decidedAt, decisionReason);
    }

    /**
     * 却下する（US30-7）。
     *
     * <p><strong>理由は必須である。</strong>理由は申請した営業担当者が読む——次にどうするかが
     * 分かるように書いてもらう必要がある。
     */
    public CancellationRequest reject(String decidedBy, String decisionReason, Instant decidedAt) {
        requireAwaitingDecision();
        if (decisionReason == null || decisionReason.isBlank()) {
            throw new IllegalArgumentException("却下の理由を入力してください");
        }
        return new CancellationRequest(id, cargoId, reason, CancellationStatus.REJECTED,
                requestedBy, requestedAt, bookingStatusAtRequest, null,
                requireDecider(decidedBy), decidedAt, decisionReason);
    }

    private void requireAwaitingDecision() {
        if (!status.awaitingDecision()) {
            throw new IllegalStateException(
                    "この申請はすでに%sです".formatted(status.label()));
        }
    }

    private static String requireDecider(String decidedBy) {
        if (decidedBy == null || decidedBy.isBlank()) {
            throw new IllegalArgumentException("決定した担当者は必須です");
        }
        return decidedBy;
    }

    /** 判断を待っているか。**未決着の申請は貨物あたり 1 件まで**。 */
    public boolean awaitingDecision() {
        return status.awaitingDecision();
    }

    public Long id() {
        return id;
    }

    public Long cargoId() {
        return cargoId;
    }

    public String reason() {
        return reason;
    }

    public CancellationStatus status() {
        return status;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    /** 申請時点の予約状態。**キャンセル料の料率の根拠**（US23・IT11）。 */
    public BookingStatus bookingStatusAtRequest() {
        return bookingStatusAtRequest;
    }

    public Optional<String> dischargeLocation() {
        return Optional.ofNullable(dischargeLocationUnLocode);
    }

    public Optional<String> decidedBy() {
        return Optional.ofNullable(decidedBy);
    }

    public Optional<Instant> decidedAt() {
        return Optional.ofNullable(decidedAt);
    }

    public Optional<String> decisionReason() {
        return Optional.ofNullable(decisionReason);
    }
}
