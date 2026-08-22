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
    CONSULTATION_REQUESTED;

    /**
     * この状態の予約を経路設計者に開いてよいか（[ADR-015] 決定 5・[ADR-020] 決定 3・決定 7）。
     *
     * <p><strong>判定はここ 1 か所に置く。</strong>一覧と詳細で別々に書くと、片方を広げても
     * もう片方が古い範囲のままになる。IT5 では詳細だけが `ROUTED` を開き、一覧が落としていた。
     *
     * <p>開くのは 3 つ。引き渡された予約（作業待ち）、経路が決まった予約（差し替えのため）、
     * 営業へ差し戻した予約（話がついたあとに続きをするため）。
     */
    public boolean visibleToRoutingPlanner() {
        return this != NOT_ROUTED;
    }

    /** 経路設計者に開いてよい状態の一覧。一覧の絞り込みはここから導く。 */
    public static java.util.List<RoutingStatus> openToRoutingPlanner() {
        return java.util.Arrays.stream(values())
                .filter(RoutingStatus::visibleToRoutingPlanner)
                .toList();
    }
}
