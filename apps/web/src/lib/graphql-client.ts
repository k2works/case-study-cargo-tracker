import type { TypedDocumentNode } from '@graphql-typed-document-node/core';
import { GraphQLClient } from 'graphql-request';
import { toApiError } from './errors';

export type ApiConfig = {
  /** バックエンドの基底 URL（開発時は Vite が中継する）。省くと、開いているページと同じ origin（graphql-request は相対 URL を解釈できない）。 */
  baseUrl?: string;
  /** 認証情報のヘッダー。方式は AuthProvider の内側に閉じ込める（FE 5.1）。 */
  headers: () => Record<string, string>;
};

/**
 * GraphQL クライアント。Query（/query/graphql）と Mutation（/command/graphql）は別のエンドポイント（BE-06）。
 * 失敗は ApiError に変換する。画面はこれを直接使わず、feature/api のフックを通す（F2）。
 */
export function createApi({ baseUrl = globalThis.location?.origin ?? '', headers }: ApiConfig) {
  const client = (path: string) => new GraphQLClient(`${baseUrl}${path}`, { headers });
  const run = async <TData, TVars extends object>(path: string, document: TypedDocumentNode<TData, TVars>, variables?: TVars): Promise<TData> => {
    try {
      return await client(path).request<TData>({ document, variables } as never);
    } catch (cause) {
      throw toApiError(cause);
    }
  };
  return {
    query: <TData, TVars extends object = Record<string, never>>(document: TypedDocumentNode<TData, TVars>, variables?: TVars) =>
      run('/query/graphql', document, variables),
    command: <TData, TVars extends object>(document: TypedDocumentNode<TData, TVars>, variables: TVars) =>
      run('/command/graphql', document, variables),
  };
}

export type Api = ReturnType<typeof createApi>;
