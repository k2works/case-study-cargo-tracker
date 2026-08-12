package com.example.cargotracker.routing.domain.model.aggregates;
import com.example.cargotracker.routing.domain.model.entities.ProposedRoute;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCriteria;

import java.util.List;

/**
 * 経路提案。予約 1 件に対して算出した経路候補の集合（US08 / US09 / US10）。
 *
 * <p>Routing Context の集約ルートである。<strong>再算出のたびに候補集合を丸ごと
 * 入れ替える</strong>（{@code domain-model.md} ビジネスルール 5）。前回の候補を
 * 残すと、どの候補がどの条件で出たものか分からなくなる。
 *
 * <p><strong>候補ゼロは異常ではなく状態である。</strong> 期限が厳しい、危険物を
 * 扱える便が限られる、といった理由で 1 件も出ないことは日常的に起きる。
 * ゼロであることを保持し、経路割り当て待ち一覧に出す。
 */
public final class BookingRouteProposal {

    private final RoutingBookingId bookingId;
    private final RoutingCriteria criteria;
    private final List<ProposedRoute> candidates;
    private final int calculationCount;

    /** 選択・確定した候補（US09）。未確定は {@code null}。 */
    private final ProposedRoute selectedRoute;

    private final long version;

    private BookingRouteProposal(
            RoutingBookingId bookingId,
            RoutingCriteria criteria,
            List<ProposedRoute> candidates,
            int calculationCount,
            ProposedRoute selectedRoute,
            long version) {
        if (bookingId == null) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
        if (criteria == null) {
            throw new IllegalArgumentException("探索条件は必須です");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("候補は必須です（0 件は空のリストで表す）");
        }
        if (calculationCount < 1) {
            throw new IllegalArgumentException("算出回数は 1 以上です: " + calculationCount);
        }
        this.bookingId = bookingId;
        this.criteria = criteria;
        this.candidates = List.copyOf(candidates);
        this.calculationCount = calculationCount;
        this.selectedRoute = selectedRoute;
        this.version = version;
    }

    /** 初回の算出（US08）。 */
    public static BookingRouteProposal propose(
            RoutingBookingId bookingId,
            RoutingCriteria criteria,
            List<ProposedRoute> candidates) {
        return new BookingRouteProposal(bookingId, criteria, candidates, 1, null, 0L);
    }

    /** 永続化された状態から復元する。 */
    public static BookingRouteProposal reconstruct(
            RoutingBookingId bookingId,
            RoutingCriteria criteria,
            List<ProposedRoute> candidates,
            int calculationCount,
            ProposedRoute selectedRoute,
            long version) {
        return new BookingRouteProposal(
                bookingId, criteria, candidates, calculationCount, selectedRoute, version);
    }

    /**
     * 条件を変えて算出し直す（US08 の再実行 / US10）。
     *
     * <p>候補は<strong>入れ替える</strong>。算出回数は増える。何回試したかは、
     * 条件を緩めるかどうかの判断材料になる。
     */
    public BookingRouteProposal recalculate(
            RoutingCriteria newCriteria, List<ProposedRoute> newCandidates) {
        // **選択は解除する。** 古い候補への選択が残ると、消えた便を指したままになる
        return new BookingRouteProposal(
                bookingId, newCriteria, newCandidates, calculationCount + 1, null, version);
    }

    /**
     * 候補を 1 件選んで確定する（US09）。
     *
     * <p><strong>選べない候補は選べない。</strong> 一覧に残すこと（ビジネスルール 6）と、
     * 選択を通すことは別である。画面でボタンを無効にするだけでは、
     * URL の書き換えや再送で通ってしまう。
     *
     * @param voyageNumber 選ぶ候補の航海番号
     * @throws IllegalArgumentException 候補に無い、または選べない候補を指したとき
     */
    public BookingRouteProposal select(VoyageNumber voyageNumber) {
        if (voyageNumber == null) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        ProposedRoute target = candidates.stream()
                .filter(c -> c.voyageNumber().equals(voyageNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "候補にない航海です: " + voyageNumber.value()));
        if (!target.selectable()) {
            throw new IllegalArgumentException(target.unselectableReason());
        }
        return new BookingRouteProposal(
                bookingId, criteria, candidates, calculationCount, target, version);
    }

    /** 確定済みか。 */
    public boolean isSelected() {
        return selectedRoute != null;
    }

    /** 確定した候補。未確定は {@code null}。 */
    public ProposedRoute selectedRoute() {
        return selectedRoute;
    }

    /** 候補が 1 件も無いか。**異常ではなく状態である。** */
    public boolean hasNoCandidate() {
        return candidates.isEmpty();
    }

    /**
     * 期限内に到達できる候補が 1 件も無いか（受入基準）。
     *
     * <p>候補ゼロとは<strong>区別する</strong>。便はあるが間に合わないのか、
     * 便そのものが無いのかで、次にすべきことが違う。
     */
    public boolean hasNoDeadlineSatisfyingCandidate() {
        return candidates.stream().noneMatch(ProposedRoute::deadlineSatisfied);
    }

    public int candidateCount() {
        return candidates.size();
    }

    public RoutingBookingId bookingId() {
        return bookingId;
    }

    public RoutingCriteria criteria() {
        return criteria;
    }

    public List<ProposedRoute> candidates() {
        return candidates;
    }

    public int calculationCount() {
        return calculationCount;
    }

    public long version() {
        return version;
    }
}
