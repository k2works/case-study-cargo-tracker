import { queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

export interface AttentionItemView {
  readonly itemId: string;
  readonly kind: string;
  readonly targetType: string;
  readonly targetId: string;
  readonly assignedRole: string;
  readonly reason: string;
  /**
   * 重複相手の荷主。サーバが payload のメールアドレスから引いた識別子だけを返す。
   * payload そのものは応答に載らない（ADR-0003。載せると鍵を破棄しても
   * 要確認一覧に平文の個人情報が残る）。
   */
  readonly relatedShipperId: string | null;
  readonly occurredAt: string;
}

/**
 * 要確認を持つサービス。**投影が `attention_item` に書くサービスを増やしたら、
 * ここにも足す。** 記録するだけで読み口に載せないと、弾かれたことが誰にも見えない
 * まま残る（IT3 で routing がその状態だった）。
 */
const SOURCES = ['/booking/attention-items', '/routing/attention-items'] as const;

/**
 * 自分の担当宛の要確認だけを取る。
 *
 * <p>ロールは送らない。Gateway が JWT から取り出して伝える。クライアントが
 * 指定できると、他ロール宛の要確認まで見えてしまう。</p>
 *
 * <p>要確認は BC ごとの読み取りモデルに散っているので、画面の側で束ねる。
 * どれか 1 つが「反映がまだ」なら、その旨をそのまま伝える（一部だけ出して
 * 「これで全部」に見せると、見落としが起きる）。</p>
 */
export async function fetchAttentionItems(): Promise<Pending<{ items: AttentionItemView[] }>> {
  const results = await Promise.all(
    SOURCES.map((path) => queryClient<{ items: AttentionItemView[] }>(path)),
  );

  const notReady = results.find((result) => result.state === 'pending');
  if (notReady?.state === 'pending') {
    return notReady;
  }

  const items = results.flatMap((result) => (result.state === 'ready' ? result.value.items : []));
  return {
    state: 'ready',
    value: {
      items: [...items].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt)),
    },
  };
}
