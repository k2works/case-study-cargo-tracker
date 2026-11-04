import { useCallback, useState } from 'react';

/**
 * コマンドの冪等性キー（ADR-003・FE 4.2）。
 * 画面を開いた時点で 1 つ作り、送信ボタンの再押下と通信エラーの再送では同じキーを使う。
 * 成功したら `reset()` で捨てる（同じ入力を意図して 2 回登録するときは新しいキーにする）。
 */
export function useIdempotencyKey(): { key: string; reset: () => void } {
  const [key, setKey] = useState(() => crypto.randomUUID());
  const reset = useCallback(() => setKey(crypto.randomUUID()), []);
  return { key, reset };
}
