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
 * 自分の担当宛の要確認だけを取る。
 *
 * <p>ロールは送らない。Gateway が JWT から取り出して伝える。クライアントが
 * 指定できると、他ロール宛の要確認まで見えてしまう。</p>
 */
export function fetchAttentionItems(): Promise<Pending<{ items: AttentionItemView[] }>> {
  return queryClient('/booking/attention-items');
}
