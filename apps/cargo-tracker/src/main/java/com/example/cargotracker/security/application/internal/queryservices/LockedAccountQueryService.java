package com.example.cargotracker.security.application.internal.queryservices;

import java.util.List;

/**
 * ロック中のアカウントの読み取り（US33。CQRS のクエリ側）。
 *
 * <p>実装はインフラ層に置く（ArchUnit ルール 3）。
 */
public interface LockedAccountQueryService {

    /**
     * ロック中のアカウントを返す。
     *
     * <p><strong>ロックが切れたものは含めない。</strong> 解除する対象ではないものを
     * 並べると、管理者は「どれを解除すべきか」を自分で判断することになる。
     * 並び順はロック時刻の新しい順（直近で止まった現場が上に来る）。
     */
    List<LockedAccountView> findLocked();
}
