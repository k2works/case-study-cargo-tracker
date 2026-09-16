/**
 * 受け付けたが反映がまだ、という状態を型にする。
 *
 * CQRS では「受け付けた」と「読めるようになった」が別なので、画面が両方を扱えないと
 * 「登録したのに一覧に出ない」が不具合に見える。202 を失敗にせず pending として運ぶ。
 */
export type Pending<T> =
  | { readonly state: 'ready'; readonly value: T }
  | { readonly state: 'pending'; readonly message: string };

export function ready<T>(value: T): Pending<T> {
  return { state: 'ready', value };
}

export function pending<T>(message: string): Pending<T> {
  return { state: 'pending', message };
}

export function isPending<T>(result: Pending<T>): boolean {
  return result.state === 'pending';
}
