/**
 * BC をまたいで伝播するドメインイベント。
 *
 * <p><strong>共有カーネル（{@code shared.domain.model}）ではない。</strong> ここに置くのは
 * 「起きた事実」だけであり、業務の判断は含まない。ADR-005 が 2 要素に限っているのは
 * 共有カーネルの話であり、イベントはその制限の対象ではない。
 *
 * <p><strong>運ぶのは素の値だけである。</strong> 発行側の型を載せると、購読する BC が
 * 発行側のドメインを参照することになる。イベントは ACL ポートと同じく
 * <strong>境界を越える数少ない通り道</strong>であり、同じ規律が要る。
 *
 * <p>購読は {@code @TransactionalEventListener(AFTER_COMMIT)} で行う（ADR-009）。
 * <strong>発行側のトランザクションがコミットしてから購読側が動く。</strong>
 * コミット前に動くと、発行側が巻き戻ったときに購読側だけが残る。
 */
package com.example.cargotracker.shared.domain.event;
