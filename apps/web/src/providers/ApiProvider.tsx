import { createContext, useContext, type ReactNode } from 'react';
import type { Api } from '@/lib/graphql-client';

const ApiContext = createContext<Api | null>(null);

export function ApiProvider({ api, children }: { api: Api; children: ReactNode }) {
  return <ApiContext.Provider value={api}>{children}</ApiContext.Provider>;
}

/** feature の api フックだけが使う（画面は直接使わない。F2）。 */
export function useApi(): Api {
  const api = useContext(ApiContext);
  if (!api) throw new Error('ApiProvider の内側で使ってください');
  return api;
}
