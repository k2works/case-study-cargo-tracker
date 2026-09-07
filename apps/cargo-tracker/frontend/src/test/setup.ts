import '@testing-library/jest-dom/vitest';

/**
 * jsdom の `localStorage` をグローバルに戻す。
 *
 * <p><b>Node 25 は `localStorage` を組み込みで持つ。</b> `--localstorage-file` を
 * 渡していないので中身は空の器で、`getItem` すら生えていない。Vitest は
 * 「すでにグローバルにある名前」を jsdom のもので上書きしない（`getWindowKeys` は
 * `k in global` なら自分の `KEYS` に載っている名前だけを通す）ので、
 * <b>`localStorage` だけが Node の器のまま残る</b>。`sessionStorage` は Node に
 * 無いので jsdom のものが入り、片方だけ動くという分かりにくい形になる。</p>
 *
 * <p><b>「使っていないこと」を確かめる検査が壊れる。</b> 保存先が sessionStorage で
 * あること（共用端末で次の人が入れない）は `authStore.test.ts` が
 * `localStorage.getItem(...)` が null であることで固定している。器のままだと
 * その呼び出し自体が落ち、実装が正しいのに赤くなる。</p>
 *
 * <p>Vitest が jsdom の窓を `globalThis.jsdom` に置いているので、そこから取り直す。</p>
 */
const jsdomWindow = (globalThis as { jsdom?: { window: Window } }).jsdom?.window;
if (jsdomWindow !== undefined && typeof globalThis.localStorage?.getItem !== 'function') {
  Object.defineProperty(globalThis, 'localStorage', {
    value: jsdomWindow.localStorage,
    configurable: true,
    writable: true,
  });
}
