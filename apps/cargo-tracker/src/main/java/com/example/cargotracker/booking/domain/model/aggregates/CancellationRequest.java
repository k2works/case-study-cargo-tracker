package com.example.cargotracker.booking.domain.model.aggregates;
import com.example.cargotracker.booking.domain.model.valueobjects.CancellationFeeRate;
import com.example.cargotracker.booking.domain.model.valueobjects.CancellationStatus;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Instant;

/**
 * 輸送中の予約キャンセルの申請（US30。遷移表 #10）。
 *
 * <p><strong>輸送開始前とは扱いを分ける。</strong> 輸送開始前は営業担当者の操作で
 * 即座にキャンセルできる（遷移表 #9）。輸送中は<strong>貨物が船の上にある</strong>ため、
 * どこで降ろすかを決めないままキャンセルすると貨物が宙に浮く。
 *
 * <p><strong>値オブジェクトにしない。</strong> 却下された申請は記録として残り、
 * 荷主は改めて申請できる。<strong>1 つの予約に複数の申請が並ぶ</strong>ため、
 * 独自の ID と版を持つエンティティである（US36 の {@code CorrectionRequest} と同型）。
 *
 * <p><strong>申請しても予約は輸送中のままである。</strong> 承認されるまで
 * キャンセルは確定しない — 降ろす場所が決まるまで運び続けるほうが安全である。
 */
public class CancellationRequest {

    /** まだ永続化されていない申請の ID。 */
    private static final long UNSAVED = 0L;

    /** 理由の上限（DB の列と揃える）。 */
    public static final int REASON_MAX_LENGTH = 500;

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
     * @param by                決定者。未決なら {@code null}
     * @param at                決定日時。未決なら {@code null}
     * @param reason            却下の理由。承認・未決なら {@code null}
     * @param dischargeLocation 陸揚げ地。<strong>承認のときだけ値を持つ</strong>
     */
    public record Decision(
            CancellationStatus status, String by, Instant at, String reason,
            Location dischargeLocation) {

        /** まだ決まっていない。 */
        static Decision pending() {
            return new Decision(CancellationStatus.PENDING, null, null, null, null);
        }
    }

    private final long id;
    private final BookingId bookingId;
    private final String reason;
    private final Requester requester;

    /**
     * 申請時点の料率（US30）。
     *
     * <p><strong>承認時に計算し直さない。</strong> 申請してから承認されるまでに
     * 輸送が始まれば料率は 20% から 50% へ上がる。<strong>荷主が申請した時点の
     * 条件で請求する</strong> — 承認が遅れたことの費用を荷主に負わせない。
     */
    private final CancellationFeeRate feeRate;

    private Decision decision;
    private final long version;

    private CancellationRequest(
            long id, BookingId bookingId, String reason, Requester requester,
            CancellationFeeRate feeRate, Decision decision, long version) {
        this.id = id;
        this.bookingId = bookingId;
        this.reason = reason;
        this.requester = requester;
        this.feeRate = feeRate;
        this.decision = decision;
        this.version = version;
    }

    /**
     * 新しく申請する。
     *
     * <p><strong>理由を必須にする。</strong> 理由の無いキャンセルは、後から見ると
     * 「なぜ運んでいる途中でやめたのか」が誰にも分からない。荷主都合か、
     * こちらの遅延か、販売先の倒産かで、料率の交渉も再発防止も変わる。
     */
    public static CancellationRequest request(
            BookingId bookingId, String reason, CancellationFeeRate feeRate,
            String requestedBy, Instant requestedAt) {
        if (bookingId == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("キャンセルの理由は必須です");
        }
        if (reason.strip().length() > REASON_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "キャンセルの理由は %d 文字までです".formatted(REASON_MAX_LENGTH));
        }
        if (feeRate == null) {
            throw new IllegalArgumentException("キャンセル料の料率は必須です");
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("申請者は必須です");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("申請日時は必須です");
        }
        return new CancellationRequest(
                UNSAVED, bookingId, reason.strip(), new Requester(requestedBy, requestedAt),
                feeRate, Decision.pending(), 0L);
    }

    /** 永続化された値から復元する。<strong>ここでは検査しない。</strong> */
    public static CancellationRequest reconstruct(
            long id, BookingId bookingId, String reason, Requester requester,
            CancellationFeeRate feeRate, Decision decision, long version) {
        return new CancellationRequest(
                id, bookingId, reason, requester, feeRate, decision, version);
    }

    /**
     * 承認する（<strong>陸揚げ地を指定する</strong>）。
     *
     * <p><strong>降ろせる場所は限られる。</strong> 現在地の港か次の寄港地であり、
     * それ以外を指定すると、船が寄らない港で降ろす手配をすることになる。
     * <strong>候補は集約の外から渡す</strong> — どこに寄るかは旅程と現在地が知っている。
     *
     * <p><strong>申請した本人は承認できない。</strong> 一人で申請と承認ができるなら、
     * 承認という段階は形だけになる。
     *
     * @param candidates 選べる陸揚げ地（現在地の港と次の寄港地）
     */
    public void approve(
            Location dischargeLocation, java.util.Collection<Location> candidates,
            String approver, Instant decidedAt) {
        if (dischargeLocation == null) {
            throw new IllegalArgumentException("陸揚げ地は必須です");
        }
        if (candidates == null || !candidates.contains(dischargeLocation)) {
            throw new IllegalArgumentException(
                    "%s では降ろせません。現在地の港か次の寄港地を選んでください"
                            .formatted(dischargeLocation.unlocode()));
        }
        decide(approver, decidedAt, CancellationStatus.APPROVED, null, dischargeLocation);
    }

    /**
     * 却下する。
     *
     * <p><strong>理由を残す</strong> — 申請者は次に何をすればよいか分からない。
     */
    public void reject(String approver, Instant decidedAt, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("却下の理由は必須です");
        }
        decide(approver, decidedAt, CancellationStatus.REJECTED, reason.strip(), null);
    }

    private void decide(
            String approver, Instant at, CancellationStatus next,
            String rejectionReason, Location dischargeLocation) {
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
        this.decision = new Decision(next, approver, at, rejectionReason, dischargeLocation);
    }

    /** まだ永続化されていないか。 */
    public boolean isNew() {
        return id == UNSAVED;
    }

    /** まだ決着していないか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
    public boolean isPending() {
        return decision.status().isPending();
    }

    public long id() {
        return id;
    }

    public BookingId bookingId() {
        return bookingId;
    }

    public String reason() {
        return reason;
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

    public CancellationFeeRate feeRate() {
        return feeRate;
    }

    public Decision decision() {
        return decision;
    }

    public CancellationStatus status() {
        return decision.status();
    }

    /** 陸揚げ地。<strong>承認前・却下では {@code null}</strong>。 */
    public Location dischargeLocation() {
        return decision.dischargeLocation();
    }

    public long version() {
        return version;
    }
}
