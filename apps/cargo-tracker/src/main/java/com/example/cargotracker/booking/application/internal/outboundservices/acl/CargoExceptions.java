package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import java.time.Instant;
import java.util.List;

/**
 * 貨物に起きた例外を読む出力ポート（Booking → Tracking の ACL。IT12 の C31）。
 *
 * <p><strong>営業担当者は荷主から「どうなっているのか」と問われる当人である。</strong>
 * 例外が追跡管理者の画面にしか無いと、予約詳細を開いた営業担当者は
 * 何も分からないまま追跡側へ確かめに行くことになる（IT11 レビュー C31）。
 *
 * <p><strong>SQL で JOIN しない。</strong> 例外は Tracking の持ち物であり、
 * 越境してよいのは ACL ポートだけである（ADR-012。SQL の越境は
 * {@code MapperTableOwnershipTest} が検出する）。
 *
 * <p>運ぶのは<strong>表示のための素の値だけ</strong>である（ADR-005）。
 * {@code TrackingExceptionEvent} を渡すと Booking が Tracking のドメインを
 * 参照することになる（ArchUnit ルール 4）。
 */
public interface CargoExceptions {

    /**
     * 追跡番号から例外を引く（<strong>読み取り専用</strong>）。
     *
     * <p>並び順は<strong>発生の新しい順</strong>。いま何が起きているかを先に読む。
     *
     * @return 例外が無ければ空のリスト。**追跡が始まっていない貨物でも空**
     */
    List<ExceptionSummary> findByTrackingNumber(String trackingNumber);

    /**
     * 例外 1 件（表示用）。
     *
     * <p><strong>{@code Exception} で終わる名前にしない。</strong> 例外は業務で
     * 起きた事実の記録であって、Java の例外ではない。名前が型の役割と食い違うと、
     * 読む人が {@code throw} できるものだと誤解する（SpotBugs も同じ理由で咎める）。
     *
     * @param typeLabel       例外種別の表示名
     * @param occurredAt      発生日時
     * @param description     状況
     * @param resolved        対応済か。<strong>荷主に説明するとき、片づいた話と
     *                        現在進行中の話は別である</strong>
     * @param resolutionNotes 対応内容。未解決なら {@code null}
     */
    record ExceptionSummary(
            String typeLabel, Instant occurredAt, String description,
            boolean resolved, String resolutionNotes) {
    }
}
