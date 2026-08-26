package com.example.bookingms.domain.model;

/**
 * 経路の状況（ADR-009 / ADR-015）。
 *
 * <p>「まだ経路が付いていない」と「経路設計を依頼した」は、経路設計者から見て別の状態である。
 * 依頼した予約だけを取り出せないと、経路設計者は全件を見て回ることになる。
 */
public enum RoutingStatus {
    /** まだ何も始まっていない。空欄ではなく意味のある状態（ADR-009）。 */
    NOT_ROUTED,

    /** 営業担当者が経路設計を依頼した。経路設計者の作業待ち（US06）。 */
    ROUTING_REQUESTED,

    /** 経路が決まって予約に紐付いた（US09 以降で使う）。 */
    ROUTED,

    /**
     * 条件では経路が組めず、営業へ差し戻した（US10・[ADR-020] 決定 7）。
     *
     * <p>通知の仕組みが無いため、US06 と同じ形（状態を持たせて一覧で気づかせる）で代替する。
     * 「見つかりませんでした」で終わらせると、経路設計者の画面の中で行き止まりになり、
     * 荷主との条件交渉が始まらない。
     */
    CONSULTATION_REQUESTED,

    /**
     * 予定ルート外の場所で荷役が行われた（US28・[ADR-023] 決定 3・[ADR-026] 決定 2）。
     *
     * <p><strong>経路は割り当て済みだが、貨物がその経路から外れている。</strong>
     * 目的地までの経路を組み直す必要があり、直すのは経路設計者である。
     *
     * <p><strong>この列挙に進行の並びは無い。</strong>判定は述語で行う——値を足したときに
     * 述語が扱いを決め忘れると、その予約が一覧から消える（`RoutingStatusTest` が守る）。
     */
    MISROUTED;

    /**
     * この状態の予約を経路設計者に開いてよいか（[ADR-015] 決定 5・[ADR-020] 決定 3・決定 7）。
     *
     * <p><strong>判定はここ 1 か所に置く。</strong>一覧と詳細で別々に書くと、片方を広げても
     * もう片方が古い範囲のままになる。IT5 では詳細だけが `ROUTED` を開き、一覧が落としていた。
     *
     * <p>開くのは 4 つ。引き渡された予約（作業待ち）、経路が決まった予約（差し替えのため）、
     * 営業へ差し戻した予約（話がついたあとに続きをするため）、<strong>誤配の予約</strong>
     * （組み直すのは経路設計者である——落とすと直す人に見えない）。
     */
    public boolean visibleToRoutingPlanner() {
        // **すべての値を明示的に扱う**（[ADR-026] 決定 2）。default は置かない——
        // 値を足したときにここでコンパイルが止まり、扱いを決めるまで先へ進めない。
        // 否定リスト（this != NOT_ROUTED）に戻すと、足した値が自動的に「開く」方向へ
        // 倒れる。開くべきでない値を足したとき、誰も気づかないまま経路設計者の一覧に
        // 現れることになる
        return switch (this) {
            // 引き渡された予約。経路設計者の作業待ちであり、これが本来の対象
            case ROUTING_REQUESTED -> true;
            // 経路が決まった予約。差し替えのために開く
            case ROUTED -> true;
            // 営業へ差し戻した予約。話がついたあとに続きをするために開く
            case CONSULTATION_REQUESTED -> true;
            // 誤配の予約。組み直すのは経路設計者である——落とすと直す人に見えない
            case MISROUTED -> true;
            // まだ何も始まっていない。依頼された予約だけを取り出せることが目的なので落とす
            case NOT_ROUTED -> false;
        };
    }

    /** 経路設計者に開いてよい状態の一覧。一覧の絞り込みはここから導く。 */
    public static java.util.List<RoutingStatus> openToRoutingPlanner() {
        return java.util.Arrays.stream(values())
                .filter(RoutingStatus::visibleToRoutingPlanner)
                .toList();
    }
}
