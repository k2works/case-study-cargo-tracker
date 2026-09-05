package com.example.cargotracker.booking.application.port;

import com.example.cargotracker.booking.domain.model.valueobjects.RouteCandidate;
import java.util.List;

/**
 * 経路候補を探す（US08）。<b>ポートは利用側が定義する</b>（architecture_backend.md）。
 *
 * <p>routingms の型は持ち込まない。届いた契約 DTO は実装（ACL）が自 BC の型へ
 * 組み直す。持ち込むと、routingms の都合で bookingms のドメインが動く。</p>
 */
public interface RouteCandidateFinder {

    /**
     * 候補を推奨順に返す。
     *
     * <p><b>0 件と「探せなかった」は別</b>である。探せなかったときは
     * {@link RouteSearchUnavailable} を投げる。空リストにすると「候補が無い」と
     * 読まれ、経路設計者は条件を変え続けることになる。</p>
     */
    RouteCandidates find(RouteSearchRequest request);

    /**
     * 探索の結果。
     *
     * @param candidates 推奨順の候補（0 件でも失敗ではない）
     * @param truncated 探索の上限で切ったか（ADR-0007）。画面に出す
     */
    record RouteCandidates(List<RouteCandidate> candidates, boolean truncated) {

        public RouteCandidates {
            candidates = List.copyOf(candidates);
        }
    }

    /** 経路設計サービスに問い合わせられなかった。<b>候補 0 件と混同しない。</b> */
    class RouteSearchUnavailable extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public RouteSearchUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
