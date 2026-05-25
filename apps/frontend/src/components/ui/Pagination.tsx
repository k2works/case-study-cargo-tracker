interface PaginationProps {
  page: number;        // 0 始まり
  size: number;        // 1 ページあたり件数
  totalCount: number;  // 全件数
  onPageChange: (page: number) => void;
}

/**
 * 共通ページネーションコンポーネント（IT2）。
 *
 * <p>0 始まりの page と全件数 totalCount から前/次ボタンを描画し、
 * 現在ページの件数範囲を「X-Y / Z 件」形式で表示する。
 * 1 ページのみの場合は前後ボタンは無効化される。</p>
 */
export default function Pagination({ page, size, totalCount, onPageChange }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(totalCount / size));
  const safePage = Math.min(Math.max(page, 0), totalPages - 1);
  const firstIndex = totalCount === 0 ? 0 : safePage * size + 1;
  const lastIndex = Math.min(totalCount, (safePage + 1) * size);

  return (
    <nav
      aria-label="ページネーション"
      className="mt-4 flex items-center justify-between border-t pt-3"
    >
      <div className="text-sm text-gray-600" role="status">
        {totalCount === 0
          ? '0 件'
          : `${firstIndex}-${lastIndex} / ${totalCount} 件`}
      </div>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => onPageChange(safePage - 1)}
          disabled={safePage <= 0}
          className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          前へ
        </button>
        <span className="text-sm text-gray-600" aria-live="polite">
          {safePage + 1} / {totalPages}
        </span>
        <button
          type="button"
          onClick={() => onPageChange(safePage + 1)}
          disabled={safePage >= totalPages - 1}
          className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          次へ
        </button>
      </div>
    </nav>
  );
}
