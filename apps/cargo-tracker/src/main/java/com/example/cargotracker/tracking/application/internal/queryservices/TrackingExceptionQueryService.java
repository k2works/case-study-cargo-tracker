package com.example.cargotracker.tracking.application.internal.queryservices;

import java.util.List;
import java.util.Optional;

/**
 * 例外イベントの読み取り（US19 / US20。CQRS のクエリ側）。
 *
 * <p>実装はインフラ層に置く（ArchUnit ルール 3）。
 */
public interface TrackingExceptionQueryService {

    /**
     * 例外を一覧で引く。
     *
     * <p><strong>並び順は「未解決が先、発生の新しい順」</strong>（{@code ui_design.md}）。
     * この一覧は追跡管理者にとって「連絡すべき仕事の待ち行列」であり、
     * 片づいたものが上に来ると、いま何をすべきかが読めない。
     *
     * @param unresolvedOnly 未解決だけに絞るか
     * @param escalatedOnly  エスカレーション対象だけに絞るか（US20。管理者が見る）
     */
    List<TrackingExceptionView> search(boolean unresolvedOnly, boolean escalatedOnly);

    /** 例外 1 件（解決画面）。 */
    Optional<TrackingExceptionView> findById(long exceptionId);

    /**
     * 未解決の件数（ダッシュボードのカード）。
     *
     * <p><strong>件数を出す理由は「開かなくても仕事の有無が分かる」ことである。</strong>
     * カードを開くまで 0 件かどうか分からないと、毎朝すべてのカードを開くことになる。
     */
    int countUnresolved(boolean escalatedOnly);
}
