import { commandClient, queryClient } from '@/shared/api/client';
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
  /**
   * どのサービスから読んだか。**サーバは返さない**——画面が束ねた時点で分かる
   * ことなので、応答に載せると出典が 2 か所になる。
   *
   * <p>確認済にするときの宛先に要る。取り違えると 404 になるだけで、相手の
   * 一覧には残り続ける（消えたように見えて消えていない）。</p>
   */
  readonly source: string;
}

/**
 * 要確認を持つサービス。**投影が `attention_item` に書くサービスを増やしたら、
 * ここにも足す。** 記録するだけで読み口に載せないと、弾かれたことが誰にも見えない
 * まま残る（IT3 で routing がその状態だった）。
 */
const SOURCES = [
  '/booking/attention-items',
  '/routing/attention-items',
  // 請求（IT13）。算出できなかった予約と、二重に作られた請求書の拒否が経理宛に出る。
  '/billing/attention-items',
] as const;

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

  const items: AttentionItemView[] = results.flatMap((result, index) => {
    // **出典を index で結び直す。** 応答の順は SOURCES の順と同じ（Promise.all）。
    const source = SOURCES[index] ?? SOURCES[0];
    return result.state === 'ready'
      ? result.value.items.map((item) => ({ ...item, source }))
      : [];
  });
  return {
    state: 'ready',
    value: {
      items: [...items].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt)),
    },
  };
}

/**
 * 確認済にする（IT14 引き継ぎ A）。
 *
 * <p><b>読み出した元のサービスへ送る。</b> 要確認は BC ごとの読み取りモデルに
 * 散っており、宛先を間違えると 404 になるだけで、担当の一覧には残り続ける。</p>
 *
 * <p>ロールと利用者名は送らない。Gateway が JWT から取り出して伝える
 * （ADR-0001 決定 4）。クライアントが名乗れると、他人の名前で跡を残せる。</p>
 */
export async function acknowledgeAttentionItem(item: AttentionItemView): Promise<void> {
  await commandClient<unknown>(`${item.source}/${item.itemId}/acknowledge`, {});
}

/**
 * 作れなかった請求を作り直す（IT14 引き継ぎ B）。
 *
 * <p><b>要確認の操作なのでここに置く。</b> 機能どうしを直接 import しない
 * （billing の api から引くと、要確認一覧が請求の画面に依存する）。</p>
 *
 * <p>材料が足りずに請求書ができなかった予約は、要確認一覧に出たまま締めの
 * 母集団から落ち続けていた。材料が直ったら、ここから請求へ戻す。</p>
 *
 * <p><b>受け付けるのは要確認に出ている予約だけ</b>（サーバが確かめる）。
 * それ以外を通すと、連鎖が止まっていることを手で回して隠すことになる。</p>
 */
export function recalculateInvoice(bookingId: string): Promise<{ invoiceId: string }> {
  return commandClient('/billing/invoices/recalculate', { bookingId });
}
