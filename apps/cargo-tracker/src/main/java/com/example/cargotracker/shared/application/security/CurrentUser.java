package com.example.cargotracker.shared.application.security;

import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import java.util.Optional;

/**
 * いま操作している利用者を読む（US34）。
 *
 * <p><strong>画面が認証の仕組みを直接触らないための境界である。</strong>
 * 実装は {@code shared/infrastructure} が持ち、Spring Security の
 * {@code SecurityContext} から読む。
 */
public interface CurrentUser {

    /**
     * いまの利用者に紐づく荷主。
     *
     * <p><strong>社内利用者では空を返す</strong>（絞り込まないことを意味する）。
     * 荷主ロールで紐付けが無い場合も空だが、**その場合は 0 件に絞るのが正しい**。
     * 「空 = 絞らない」と読むと、紐付けを忘れた荷主に全社の予約が見える。
     * <strong>絞るかどうかはロールで決める</strong>（{@link #scopedToShipper()}）。
     */
    Optional<ShipperId> linkedShipperId();

    /**
     * 荷主として絞り込むべき利用者か。
     *
     * <p>ロールで決める。**紐付けの有無で決めない** — 紐付けを忘れた荷主が
     * 全社の予約を見る形になるためである。
     */
    boolean scopedToShipper();
}
