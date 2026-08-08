package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.shared.application.paging.Page;
import com.example.cargotracker.shared.application.paging.PageRequest;
import java.util.Optional;

/**
 * 貨物予約の読み取り（CQRS のクエリ側）。
 *
 * <p>実装はインフラ層に置く（ArchUnit ルール 3）。
 */
public interface BookingQueryService {

    /**
     * 一覧を取得する（絞り込み条件はすべて任意）。
     *
     * @param origin         出発地 UN/LOCODE。未指定なら絞り込まない
     * @param destination    目的地 UN/LOCODE。未指定なら絞り込まない
     * @param status         予約状態。未指定なら絞り込まない
     * @param trackingNumber 追跡番号。部分一致・大小文字を問わない（IT6 レビュー H9）。
     *                       荷主から「番号を無くした」の電話に答えるための入口である
     * @param page           ページ送りの要求
     */
    Page<BookingView> search(
            String origin, String destination, String status,
            String trackingNumber, PageRequest page);

    /**
     * 経路割り当て待ちの予約（US06 / US08。経路設計者の作業入口）。
     *
     * <p>対象は引き渡し済み（{@code ROUTE_PROPOSED}）で経路が未割り当てのもの。
     * <strong>既定の並び順は希望期限の昇順</strong>である（`ui_design.md`）。
     * 経路設計者が朝に見るのは「どれが一番切羽詰まっているか」であり、
     * **予約 ID 順では役に立たない**。
     */
    Page<BookingView> findAwaitingRouting(PageRequest page);

    /**
     * 追跡番号発行待ちの予約（US14。追跡管理者の作業入口）。
     *
     * <p>対象は確定済み（{@code CONFIRMED}）で追跡番号が未発行のもの。
     * <strong>ADR-006 により通知は送らない。</strong> 確定した予約がここに現れることが、
     * 業務上の「発行依頼」である（US13 の受入基準）。
     *
     * <p><strong>既定の並び順は希望期限の昇順</strong>である。追跡管理者が朝に見るのは
     * 「どれが一番切羽詰まっているか」であり、予約 ID 順では役に立たない
     * （経路割り当て待ちと同じ理由）。
     */
    Page<BookingView> findAwaitingTracking(PageRequest page);

    /**
     * 追跡中の貨物（US17 の作業対象）。
     *
     * <p><strong>状態を手で更新するには、まず対象にたどり着けなければならない。</strong>
     * 発行待ち一覧は発行した時点でその予約が消えるため、発行後の貨物へ画面から
     * 到達する手段が無かった。追跡番号を覚えている追跡管理者はいない。
     */
    Page<BookingView> findInTransit(PageRequest page);

    Optional<BookingView> findById(String bookingId);
}
