/**
 * 一覧 API 共通のページネーションレスポンス（ADR-0008）。
 *
 * <p>バックエンドの `PageResponse<T>` DTO（items / totalCount / page / size）に対応する。</p>
 */
export interface PageResponse<T> {
  items: T[];
  totalCount: number;
  page: number;
  size: number;
}

/** 一覧のデフォルトページサイズ（ADR-0008。上限はサーバー側で 200 にサニタイズ）。 */
export const DEFAULT_PAGE_SIZE = 20;
