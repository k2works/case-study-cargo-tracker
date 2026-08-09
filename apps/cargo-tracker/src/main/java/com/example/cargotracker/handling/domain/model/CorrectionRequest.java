package com.example.cargotracker.handling.domain.model;

import java.time.Instant;

/**
 * 引取記録の訂正・取り消し申請（US36）。
 *
 * <p>引取は輸送の終点であり、<strong>誤登録をそのままにすると貨物が届いていないのに
 * 配送完了として扱われる</strong>。
 *
 * <p><strong>申請と承認を分ける。</strong> 現場が自分で取り消せると、
 * 引き渡しの証明（US35）が現場の判断で消せることになる。
 *
 * <p><strong>元の記録は消さない。</strong> 誰がいつ何を登録し、誰がいつ取り消したかが
 * 読めなくなると、事故時に経緯を追えない。
 */
public class CorrectionRequest {

    /** まだ永続化されていない申請の ID。 */
    private static final long UNSAVED = 0L;

    /**
     * 申請の中身（種別・理由・訂正で置き換える値）。
     *
     * <p><strong>ひと組で持つ。</strong> 引数を並べると、13 個目で Checkstyle が
     * 止めた。<strong>制限に当たったのは合図である</strong>
     * （{@code Cargo.reconstruct} で同じ判断をした）。
     *
     * @param correctedCompletionTime 訂正後の作業日時。取り消し・未指定なら {@code null}
     * @param correctedNote           訂正後のメモ。取り消し・未指定なら {@code null}
     */
    public record Details(
            CorrectionRequestType type, String reason,
            Instant correctedCompletionTime, String correctedNote) {
    }

    /**
     * 誰がいつ申請したか。
     *
     * <p><strong>申請者は承認の可否を決める値である</strong>（本人は承認できない）。
     */
    public record Requester(String name, Instant at) {
    }

    /**
     * 決定（承認・却下）。
     *
     * @param by     決定者。未決なら {@code null}
     * @param at     決定日時。未決なら {@code null}
     * @param reason 却下の理由。承認・未決なら {@code null}
     */
    public record Decision(CorrectionStatus status, String by, Instant at, String reason) {

        /** まだ決まっていない。 */
        static Decision pending() {
            return new Decision(CorrectionStatus.PENDING, null, null, null);
        }
    }

    private final long id;
    private final long handlingActivityId;
    private final Details details;
    private final Requester requester;
    private Decision decision;
    private final long version;

    private CorrectionRequest(
            long id, long handlingActivityId, Details details,
            Requester requester, Decision decision, long version) {
        this.id = id;
        this.handlingActivityId = handlingActivityId;
        this.details = details;
        this.requester = requester;
        this.decision = decision;
        this.version = version;
    }

    /**
     * 新しく申請する。
     *
     * <p><strong>理由を必須にする。</strong> 理由の無い取り消しは、後から見ると
     * 「なぜ配送完了が取り消されたのか」が誰にも分からない。
     */
    public static CorrectionRequest request(
            long handlingActivityId, CorrectionRequestType type, String reason,
            String requestedBy, Instant requestedAt) {
        if (type == null) {
            throw new IllegalArgumentException("訂正か取り消しかを選んでください");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("理由は必須です");
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("申請者は必須です");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("申請日時は必須です");
        }
        return new CorrectionRequest(
                UNSAVED, handlingActivityId,
                new Details(type, reason.strip(), null, null),
                new Requester(requestedBy, requestedAt), Decision.pending(), 0L);
    }

    /**
     * 訂正の内容を添えて申請する（US36）。
     *
     * <p>取り消しでは使わない。<strong>直す中身が無い取り消しに
     * 「直した後の値」を持たせない。</strong>
     */
    public CorrectionRequest correcting(Instant completionTime, String note) {
        if (details.type() != CorrectionRequestType.CORRECT) {
            throw new IllegalArgumentException("取り消しの申請に訂正内容は指定できません");
        }
        if (completionTime == null && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("訂正する内容を入力してください");
        }
        return new CorrectionRequest(
                id, handlingActivityId,
                new Details(details.type(), details.reason(), completionTime, note),
                requester, decision, version);
    }

    /** 永続化された値から復元する。<strong>ここでは検査しない。</strong> */
    public static CorrectionRequest reconstruct(
            long id, long handlingActivityId, Details details,
            Requester requester, Decision decision, long version) {
        return new CorrectionRequest(id, handlingActivityId, details, requester, decision, version);
    }

    /**
     * 承認する。
     *
     * <p><strong>申請した本人は承認できない。</strong> 一人で申請と承認ができるなら、
     * 承認という段階は形だけになる。<strong>「追跡管理者の承認なしには状態が
     * 戻らない」という受入基準は、別人であって初めて満たされる。</strong>
     *
     * <p><strong>二度は決められない。</strong> 決め直しを許すと、却下されたものを
     * 後から承認でき、決定の日時と決定者が上書きされる。
     */
    public void approve(String approver, Instant decidedAt) {
        decide(approver, decidedAt, CorrectionStatus.APPROVED, null);
    }

    /** 却下する。<strong>理由を残す</strong> — 申請者は次に何をすればよいか分からない。 */
    public void reject(String approver, Instant decidedAt, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("却下の理由は必須です");
        }
        decide(approver, decidedAt, CorrectionStatus.REJECTED, reason.strip());
    }

    private void decide(
            String approver, Instant at, CorrectionStatus next, String reason) {
        if (!decision.status().isPending()) {
            throw new IllegalStateException(
                    "この申請はすでに%sです。同じ申請を二度決めることはできません"
                            .formatted(decision.status().displayName()));
        }
        if (approver == null || approver.isBlank()) {
            throw new IllegalArgumentException("承認者は必須です");
        }
        if (approver.equals(requester.name())) {
            throw new IllegalArgumentException(
                    "申請した本人は承認できません。別の追跡管理者に依頼してください");
        }
        if (at == null) {
            throw new IllegalArgumentException("決定日時は必須です");
        }
        this.decision = new Decision(next, approver, at, reason);
    }

    /** まだ永続化されていないか。 */
    public boolean isNew() {
        return id == UNSAVED;
    }

    public long id() {
        return id;
    }

    public long handlingActivityId() {
        return handlingActivityId;
    }

    public Details details() {
        return details;
    }

    public CorrectionRequestType type() {
        return details.type();
    }

    public String reason() {
        return details.reason();
    }

    public Requester requester() {
        return requester;
    }

    public String requestedBy() {
        return requester.name();
    }

    public Instant requestedAt() {
        return requester.at();
    }

    public Decision decision() {
        return decision;
    }

    public CorrectionStatus status() {
        return decision.status();
    }

    public long version() {
        return version;
    }
}
