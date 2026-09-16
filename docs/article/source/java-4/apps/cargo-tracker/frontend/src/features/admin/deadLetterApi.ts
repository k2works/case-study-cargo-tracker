import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

/**
 * 退避した 1 件（S91 / IT15 引き継ぎ 1）。
 *
 * **中身（payload）は載らない。** 退避されたイベントには個人情報が載りうるので
 * （ADR-0003）、サーバが返さない。一覧に要るのは「どの処理が・どの種類の
 * イベントで・なぜ止まったか」である。
 */
export interface DeadLetterView {
  readonly deadLetterId: string;
  readonly processingGroup: string;
  readonly sequenceIdentifier: string;
  readonly eventType: string;
  readonly eventIdentifier: string;
  readonly enqueuedAt: string | null;
  readonly causeType: string | null;
  readonly causeMessage: string | null;
  /**
   * どのサービスから読んだか。**サーバは返さない**——画面が束ねた時点で分かる
   * ことなので、応答に載せると出典が 2 か所になる（要確認一覧と同じ形）。
   *
   * 処理し直すときの宛先に要る。
   */
  readonly source: string;
}

/**
 * 退避先を持つサービス。**DLQ を宣言した Processing Group を持つサービスを
 * 増やしたら、ここにも足す。** 載せないと、そのサービスだけ止まっていることが
 * 誰にも見えない。
 */
const SOURCES = [
  '/booking/dead-letters',
  '/routing/dead-letters',
  '/tracking/dead-letters',
  '/handling/dead-letters',
  '/billing/dead-letters',
] as const;

/**
 * 退避しているイベントを 5 サービスから束ねる（S70 と同じ形）。
 *
 * <p>どれか 1 つが「反映がまだ」なら、その旨をそのまま伝える——一部だけ出して
 * 「これで全部」に見せると、止まっているサービスを見落とす。</p>
 */
export async function fetchDeadLetters(): Promise<Pending<{ items: DeadLetterView[] }>> {
  const results = await Promise.all(
    SOURCES.map((path) => queryClient<{ items: DeadLetterView[] }>(path)),
  );

  const notReady = results.find((result) => result.state === 'pending');
  if (notReady?.state === 'pending') {
    return notReady;
  }

  const items: DeadLetterView[] = results.flatMap((result, index) => {
    const source = SOURCES[index] ?? SOURCES[0];
    return result.state === 'ready'
      ? result.value.items.map((item) => ({ ...item, source }))
      : [];
  });
  return {
    state: 'ready',
    value: {
      items: [...items].sort((a, b) => (b.enqueuedAt ?? '').localeCompare(a.enqueuedAt ?? '')),
    },
  };
}

/**
 * 退避したイベントを処理し直す（ADR-0014 決定 1）。
 *
 * <p><b>消す手段は無い。</b> 直したあとに退避を消すのは「黙って捨てる」こと
 * である。直っていなければ、また退避される——それが正しい。</p>
 */
export async function retryDeadLetters(source: string): Promise<void> {
  await commandClient<unknown>(`${source}/retry`, {});
}
