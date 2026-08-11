package com.example.cargotracker.handling.application.internal.queryservices;

import com.example.cargotracker.handling.application.internal.outboundservices.acl
        .ApprovedCancellations;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 荷降し手配に<strong>どれが残るか</strong>という規則（IT17 の R3）。
 *
 * <p>IT16 まで、この規則は {@code MyBatisHandlingQueryService} の中にあった。
 * <strong>業務の規則が、SQL を書くクラスの中にしか無かった</strong>。確かめるには
 * 実 PostgreSQL を起動して承認と荷降しをデータで作る必要があり、
 * <strong>規則そのものを直接壊すテストが書けなかった</strong>。
 *
 * <h2>なぜ「規則」だけを動かし、「問い合わせ」は動かさないのか</h2>
 *
 * <p>CQRS の読み取り側を MyBatis で書くのは本プロジェクトの設計である
 * （{@code architecture_backend.md}「CQRS 適用方針」）。クエリサービスの実装を
 * まるごと application 層へ移すと<strong>1 つだけ例外ができ、次に一覧を書く人が
 * どちらに従えばよいか分からなくなる</strong>。ArchUnit ルール 3（アプリケーション層が
 * インフラ層を直接参照しない）を満たすためだけに、この一覧のためのポートを 1 本
 * 増やすことにもなる。
 *
 * <p>そこで<strong>動かしたのは判断だけである</strong>。何回問い合わせるか・
 * どの SQL で引くかは infrastructure に残る。ここにあるのは
 * 「降ろし済みは落とす」「同じ予約は 1 つにまとめる」「順序は保つ」という
 * <strong>業務の言葉で説明できる規則</strong>だけであり、DB を起動せずに壊せる。
 */
public final class DischargeOrderSelection {

    private DischargeOrderSelection() {
    }

    /**
     * 手配のうち<strong>まだ降ろしていないもの</strong>を、受け取った順序のまま返す。
     *
     * <p>同じ予約の手配は 1 つにまとめる。作業員から見れば「その貨物を降ろす」という
     * 1 つの仕事であり、2 行出ると片方を済ませたときにもう片方が残って見える。
     *
     * <p><strong>並べ替えない。</strong> 承認の古い順は ACL ポートの SQL が
     * {@code ORDER BY decided_at} で決めている。ここで並べ直すと規則が 2 か所に散る。
     *
     * @param orders   承認済みの手配（承認の古い順）
     * @param unloaded すでに降ろした予約
     */
    public static List<ApprovedCancellations.DischargeOrder> pending(
            List<ApprovedCancellations.DischargeOrder> orders, Set<UUID> unloaded) {
        return byBooking(orders).entrySet().stream()
                .filter(entry -> !unloaded.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    /**
     * 手配が指す予約 ID（重複を除き、受け取った順）。
     *
     * <p><strong>絞り込みに使う ID を同じ規則から取り出す。</strong> 変換と絞り込みで
     * 別々に解析すると、条件を変えたときに片方だけ直す余地が残る。
     */
    public static List<UUID> bookingIds(List<ApprovedCancellations.DischargeOrder> orders) {
        return List.copyOf(byBooking(orders).keySet());
    }

    /**
     * 予約 ID ごとの手配（重複は先に来たものを残す）。
     *
     * <p><strong>予約 ID が読めない手配は落とす。</strong> 例外にすると
     * <strong>1 件の壊れた行で一覧全体が開けなくなる</strong> —
     * 荷役作業員の手が止まるほうが重い。
     */
    private static Map<UUID, ApprovedCancellations.DischargeOrder> byBooking(
            List<ApprovedCancellations.DischargeOrder> orders) {
        Map<UUID, ApprovedCancellations.DischargeOrder> byBooking = new LinkedHashMap<>();
        for (ApprovedCancellations.DischargeOrder order : orders) {
            UUID bookingId = parse(order.bookingId());
            if (bookingId != null) {
                byBooking.putIfAbsent(bookingId, order);
            }
        }
        return byBooking;
    }

    private static UUID parse(String bookingId) {
        try {
            return UUID.fromString(bookingId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
