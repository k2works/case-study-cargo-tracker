import { createContext, useContext, type ReactNode } from 'react';

/**
 * 結果整合への対応（FE 3.2・NFR-P11・P12）：コマンドが成功しても、読み取りへの反映は遅れる。
 * 応答の版以上になるまで、短い間隔から始めて徐々に間隔を延ばして再取得し、上限を過ぎたら「反映待ち」を表示する。
 */
export type ReflectionConfig = { initialMs: number; maxMs: number; timeoutMs: number };

export const DEFAULT_REFLECTION: ReflectionConfig = { initialMs: 500, maxMs: 2000, timeoutMs: 15000 };

const ReflectionContext = createContext<ReflectionConfig>(DEFAULT_REFLECTION);

export function ReflectionConfigProvider({ value, children }: { value: ReflectionConfig; children: ReactNode }) {
  return <ReflectionContext.Provider value={value}>{children}</ReflectionContext.Provider>;
}

export const useReflectionConfig = () => useContext(ReflectionContext);
