/**
 * 画面で共通に使う見た目の定義（ui_design.md「色トークン」「レスポンシブ・
 * アクセシビリティ」）。
 *
 * <p>1 か所に集めるのは、画面ごとに書くと同じ意味の要素が画面ごとに違う
 * 見た目になり、利用者が「別のシステムに来た」と感じるためである。</p>
 *
 * <p><strong>色は設計の表に対応させる。</strong>コントラスト比が AA を満たす
 * 組み合わせとして選ばれているので、明るい方へずらすと基準を割る
 * （例: text-blue-500 は白地に 3.7 : 1 で不足）。</p>
 */

/** リンク。ui_design.md の `text-link`（#1D4ED8 on #FFFFFF、6.3 : 1）。 */
export const LINK = 'text-blue-700 underline';

/** 画面の見出し（h1）。 */
export const PAGE_TITLE = 'text-2xl font-bold text-gray-900';

/** 節の見出し（h2）。 */
export const SECTION_TITLE = 'text-lg font-semibold text-gray-900';

/** 囲み。一覧やフォームの地。 */
export const CARD = 'rounded border border-gray-200 bg-white p-6';

/** 表。横幅が足りないときは親を overflow-x-auto で包む。 */
export const TABLE = 'w-full border-collapse text-sm';
export const TABLE_CAPTION = 'sr-only';
export const TH = 'border-b border-gray-300 px-3 py-2 text-left font-semibold text-gray-700';
export const TD = 'border-b border-gray-100 px-3 py-2 text-gray-800';

/** 入力欄とラベル。 */
export const FIELD =
  'mt-1 w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none';
export const LABEL = 'block text-sm font-medium text-gray-700';

/** 主ボタン。押せないときは aria-disabled で表す（見た目もそれに従う）。 */
export const BUTTON_PRIMARY =
  'rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700'
  + ' disabled:bg-gray-300 aria-disabled:bg-gray-300';

/**
 * 取り消せない操作のボタン。ui_design.md の `badge-danger`
 * （`#991B1B` on `#FEE2E2`、7.9 : 1）に対応する色を使う。
 */
export const BUTTON_DANGER =
  'rounded bg-red-800 px-4 py-2 text-white hover:bg-red-900'
  + ' disabled:bg-gray-300 aria-disabled:bg-gray-300';

/** 補助ボタン（やめる・開く）。主導線から色で区別する。 */
export const BUTTON_SECONDARY =
  'rounded border border-gray-300 bg-white px-4 py-2 text-gray-800 hover:bg-gray-50';

/**
 * 案内。ui_design.md の `badge-pending`（#B45309 on #FFFBEB、5.9 : 1）に対応する。
 * 「まだ出ていない」「反映中」など、待たせていることを伝える場所に使う。
 */
export const NOTICE =
  'block rounded border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-700';

/** 失敗。ui_design.md の `badge-danger`（#991B1B on #FEE2E2、7.9 : 1）に対応する。 */
export const ALERT =
  'block rounded border border-red-300 bg-red-100 px-4 py-3 text-sm text-red-800';
