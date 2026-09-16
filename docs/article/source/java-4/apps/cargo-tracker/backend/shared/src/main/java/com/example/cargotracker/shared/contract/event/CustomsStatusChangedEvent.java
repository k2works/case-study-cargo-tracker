package com.example.cargotracker.shared.contract.event;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 通関状態が変わった（契約イベント / UC21・US29）。handlingms → trackingms・billingms。
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると {@code CustomsDeclaration} は空のまま
 * 復元され、「未決着の申告は 1 件」のような状態を見る守りが素通りする。
 * {@code AxonTestFixture} では判別できない（IT7 で実測）。</p>
 *
 * <p><b>購読側の投影が作れる分を運ぶ。</b> 投影はコマンドを読まないので、受け側の列を
 * 先に並べて決めた。</p>
 *
 * <ul>
 *   <li>trackingms は {@code HELD} を受けて {@code CUSTOMS_HOLD} を自動起票する
 *       （不変条件 4）。起票には<b>追跡番号</b>と、例外の説明に載せる<b>理由</b>が要る</li>
 *   <li>billingms は留置日数を調整の根拠にする（M14）。<b>予約 ID</b>で請求を引き、
 *       <b>留置営業日数</b>を読む。日数は「留置から次の状態へ変わるとき」だけ値を持つ。
 *       <b>ただし購読は US21（IT13）から。</b> billingms はまだ請求の集約を持たないので、
 *       いま受け皿を置いても記録を積むだけで誰にも読めない——<b>読む側の無い配線を
 *       先に敷かない</b>（IT9・IT10 と同じ判断）。項目はいま決める必要がある。
 *       イベントは追記専用で、後から足しても過去のイベントには入らないためである</li>
 * </ul>
 *
 * <p><b>前の状態も載せる。</b> 「留置から通関済へ」と「審査中から通関済へ」は
 * 購読側にとって別の出来事である——前者だけが保管料の調整を伴う。載せないと、
 * 購読側が自分で前の状態を覚えることになり、覚え方が BC ごとに食い違う。</p>
 */
public record CustomsStatusChangedEvent(
        @EventTag(key = "declarationNumber") String declarationNumber,
        String trackingNumber,
        String bookingId,
        String previousStatus,
        String status,
        String reason,
        // 留置から出るときの営業日数。それ以外は 0（留置していないので数えるものが無い）。
        int heldBusinessDays,
        String changedBy,
        Instant changedAt) {
}
