'use strict';

import fs from 'fs';
import path from 'path';
import { marked } from 'marked';
import plantumlEncoder from 'plantuml-encoder';

/** 変換元（ユーザーマニュアルの Markdown）。 */
const SRC_DIR = path.join(process.cwd(), 'docs', 'manual');
/**
 * 変換先。
 *
 * ポータル（apps/www）配下に置く。ドキュメントサイトのイメージは `apps/www/` を
 * まるごと配信するため、ここへ出力するだけで `/manual/` から読める。
 * 別の場所に置いて Dockerfile 側で拾う形にすると、置き場所と配信先が離れ、
 * 「生成したのに読者に届かない」状態を作りやすい（IT2 で実際に起きた）。
 */
const OUT_DIR = path.join(process.cwd(), 'apps', 'www', 'manual');
/** PlantUML レンダリングサーバ（mkdocs と同じ既定値）。 */
const PLANTUML_SERVER = (
  process.env.PLANTUML_SERVER_URL || 'http://www.plantuml.com/plantuml'
).replace(/\/$/, '');
/** マニュアルのサイトタイトル（`.env` の MANUAL_TITLE で上書きする）。 */
const MANUAL_TITLE = process.env.MANUAL_TITLE || 'ユーザーマニュアル';
/** フッターの著作権表示（未設定なら出力しない）。 */
const MANUAL_COPYRIGHT = process.env.MANUAL_COPYRIGHT || '';
/** 上位ポータルへの戻り先。 */
const MANUAL_PORTAL_URL = process.env.MANUAL_PORTAL_URL || '/';

/**
 * 見出しテキストから HTML の id（アンカー）を生成する。
 *
 * <p>本文が `{#login}` のように明示 ID を書いている場合はそちらを優先する。
 * 日本語見出しから機械的に作った ID は、見出しを言い換えた瞬間にリンクが切れる。
 *
 * @param {string} text 見出しテキスト
 * @returns {string} アンカー id
 */
function slugify(text) {
  return text
    .trim()
    .replace(/[（）()【】「」、。，,：:・〜~/／]/g, '')
    .replace(/\./g, '')
    .replace(/\s+/g, '-')
    .toLowerCase();
}

/**
 * ```plantuml フェンスを PlantUML サーバの SVG 画像に置き換える。
 * @param {string} md Markdown 本文
 * @returns {string} 置換後の Markdown
 */
function renderPlantuml(md) {
  return md.replace(/```plantuml\r?\n([\s\S]*?)```/g, (_match, code) => {
    const encoded = plantumlEncoder.encode(code.trim());
    return `<p class="plantuml"><img src="${PLANTUML_SERVER}/svg/${encoded}" alt="PlantUML 図" loading="lazy"></p>`;
  });
}

/**
 * YAML フロントマターを取り除く。
 *
 * MkDocs は解釈するが、marked はそのまま本文として出すため、
 * 変換しただけだと `title: ...` が画面の先頭に出る。
 *
 * @param {string} md Markdown 本文
 * @returns {string} フロントマターを除いた本文
 */
function stripFrontMatter(md) {
  return md.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '');
}

/**
 * 同一フォルダの `.md` リンクを `.html` に書き換える。
 *
 * <p>`../design/...` のようにマニュアルの外へ出るリンクは MkDocs 側の
 * ドキュメントを指すため、`/docs/` 配下へ向け直す。放置すると、
 * 押した先が 404 になる。
 *
 * @param {string} html HTML 文字列
 * @returns {string} 書き換え後 HTML
 */
function rewriteLinks(html) {
  return html
    // マニュアルの外（../design/ など）は MkDocs のドキュメントを指す
    .replace(
      /href="\.\.\/([^"]+?)\.md(#[^"]*)?"/g,
      (_m, rest, anchor) => `href="/docs/${rest}/${anchor || ''}"`,
    )
    // 同一フォルダの章
    .replace(
      /href="([^"/]+)\.md(#[^"]*)?"/g,
      (_m, file, anchor) => `href="${file}.html${anchor || ''}"`,
    );
}

/**
 * 見出しに id 属性を付与する。
 *
 * <p>本文に `{#id}` が書かれていればそれを使う。マニュアルは章をまたいで
 * 節を指すため、見出しの言い換えでリンクが切れないよう明示 ID を優先する。
 *
 * @param {string} html HTML 文字列
 * @returns {string} id 付与後 HTML
 */
function injectHeadingIds(html) {
  return html.replace(/<h([1-6])>([\s\S]*?)<\/h\1>/g, (_m, level, inner) => {
    const explicit = inner.match(/\{#([A-Za-z0-9_-]+)\}\s*$/);
    const text = inner.replace(/<[^>]+>/g, '').replace(/\{#[A-Za-z0-9_-]+\}\s*$/, '');
    const id = explicit ? explicit[1] : slugify(text);
    const body = inner.replace(/\s*\{#[A-Za-z0-9_-]+\}\s*$/, '');
    return `<h${level} id="${id}">${body}</h${level}>`;
  });
}

/**
 * 章の一覧（目次）を作る。
 *
 * <p>`index.md` は執筆の指針（構成・テンプレート）であり、読者向けの入口ではない。
 * 読者が最初に見るのは「どの章があるか」なので、章の一覧を生成して置く。
 *
 * @param {{file: string, title: string}[]} chapters 章の一覧
 * @returns {string} 目次ページの本文 HTML
 */
function tableOfContents(chapters) {
  const items = chapters
    .map(
      (c) =>
        `      <li><a href="${c.file}">${c.title}</a></li>`,
    )
    .join('\n');
  return `<h1>${MANUAL_TITLE}</h1>
    <p>業務担当者向けの操作手引きです。自分の仕事がシステムのどこにあたるかは
    <a href="01-業務フロー.html">業務フロー</a>から辿れます。</p>
    <ul class="manual-toc">
${items}
    </ul>`;
}

/**
 * 本文 HTML をページテンプレートで包む。
 * @param {string} title ページタイトル
 * @param {string} bodyHtml 本文 HTML
 * @param {boolean} isIndex 目次ページかどうか
 * @returns {string} 完全な HTML ドキュメント
 */
function pageTemplate(title, bodyHtml, isIndex) {
  const tocLink = isIndex
    ? ''
    : '<a class="manual-nav-link" href="index.html">← マニュアル目次</a>';
  const footer = MANUAL_COPYRIGHT
    ? `&copy; ${new Date().getFullYear()} ${MANUAL_COPYRIGHT}. All rights reserved.`
    : MANUAL_TITLE;
  const portalLink = MANUAL_PORTAL_URL
    ? `\n    <a class="manual-portal" href="${MANUAL_PORTAL_URL}">ポータルへ戻る</a>`
    : '';
  return `<!DOCTYPE html>
<html lang="ja">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title} | ${MANUAL_TITLE}</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <header class="manual-header">
    <a class="manual-home" href="index.html">${MANUAL_TITLE}</a>${portalLink}
  </header>
  <main class="manual-content">
    ${tocLink}
    ${bodyHtml}
    ${tocLink}
  </main>
  <footer class="manual-footer">${footer}</footer>
</body>
</html>
`;
}

/** 生成する CSS（読みやすさ重視のシンプルなスタイル）。 */
const STYLE_CSS = `:root { --fg: #24292f; --muted: #57606a; --border: #d0d7de; --accent: #00695c; --bg-soft: #f6f8fa; }
* { box-sizing: border-box; }
body { margin: 0; color: var(--fg); font-family: -apple-system, "Segoe UI", "Hiragino Kaku Gothic ProN", Meiryo, sans-serif; line-height: 1.8; background: #fff; }
.manual-header { display: flex; justify-content: space-between; align-items: center; gap: 1rem; padding: 0.75rem 1.5rem; background: var(--accent); color: #fff; position: sticky; top: 0; }
.manual-header a { color: #fff; text-decoration: none; }
.manual-home { font-weight: bold; }
.manual-portal { font-size: 0.85rem; opacity: 0.9; }
.manual-content { max-width: 900px; margin: 0 auto; padding: 2rem 1.5rem 4rem; }
.manual-nav-link { display: inline-block; margin: 0.5rem 0; color: var(--accent); text-decoration: none; font-size: 0.9rem; }
.manual-content h1 { font-size: 1.8rem; border-bottom: 2px solid var(--border); padding-bottom: 0.3rem; }
.manual-content h2 { font-size: 1.4rem; border-bottom: 1px solid var(--border); padding-bottom: 0.3rem; margin-top: 2.5rem; }
.manual-content h3 { font-size: 1.15rem; margin-top: 2rem; }
.manual-content h4 { font-size: 1rem; color: var(--muted); }
.manual-content a { color: #0969da; }
.manual-content table { border-collapse: collapse; width: 100%; margin: 1rem 0; font-size: 0.95rem; }
.manual-content th, .manual-content td { border: 1px solid var(--border); padding: 0.4rem 0.6rem; text-align: left; }
.manual-content th { background: var(--bg-soft); }
.manual-content code { background: var(--bg-soft); padding: 0.1rem 0.3rem; border-radius: 4px; font-size: 0.9em; }
.manual-content pre { background: var(--bg-soft); padding: 1rem; border-radius: 6px; overflow-x: auto; }
.manual-content pre code { background: none; padding: 0; }
.manual-content blockquote { margin: 1rem 0; padding: 0.5rem 1rem; border-left: 4px solid var(--border); background: var(--bg-soft); color: var(--muted); }
.manual-content img { max-width: 100%; height: auto; border: 1px solid var(--border); border-radius: 4px; }
.manual-content .plantuml img { border: none; }
.manual-toc { font-size: 1.05rem; }
.manual-toc li { margin: 0.4rem 0; }
.manual-footer { text-align: center; padding: 1.5rem; font-size: 0.8rem; color: var(--muted); border-top: 1px solid var(--border); }
`;

/**
 * docs/manual の Markdown を HTML へ変換し apps/www/manual へ出力する Gulp タスクを登録する。
 * @param {import('gulp').Gulp} gulp Gulp インスタンス
 */
export default function (gulp) {
  gulp.task('manual:build', (done) => {
    try {
      if (!fs.existsSync(SRC_DIR)) {
        // ソース未整備でも他の成果物のデプロイを止めない。
        // ただし「生成した」と誤認させないよう、出力は作らずに明示的に知らせる。
        console.log(
          `マニュアルのソースが見つかりません: ${SRC_DIR}\n`
            + 'マニュアルは生成しませんでした（UI 実装後に creating-manual スキルで執筆します）。',
        );
        done();
        return;
      }

      fs.rmSync(OUT_DIR, { recursive: true, force: true });
      fs.mkdirSync(OUT_DIR, { recursive: true });

      // 画像アセットをコピー。キャプチャが無いマニュアルは、画面の前で読む役に立たない
      const srcAssets = path.join(SRC_DIR, 'assets');
      let assets = 0;
      if (fs.existsSync(srcAssets)) {
        const outAssets = path.join(OUT_DIR, 'assets');
        fs.mkdirSync(outAssets, { recursive: true });
        for (const file of fs.readdirSync(srcAssets)) {
          if (/\.(png|jpe?g|gif|svg)$/i.test(file)) {
            fs.copyFileSync(path.join(srcAssets, file), path.join(outAssets, file));
            assets += 1;
          }
        }
      }

      // index.md は執筆の指針であって読者向けの入口ではないため、章としては出さない
      const mdFiles = fs
        .readdirSync(SRC_DIR)
        .filter((f) => f.endsWith('.md') && f !== 'index.md')
        .sort();

      const chapters = [];
      for (const mdFile of mdFiles) {
        const raw = stripFrontMatter(fs.readFileSync(path.join(SRC_DIR, mdFile), 'utf8'));
        const titleMatch = raw.match(/^#\s+(.+)$/m);
        const title = titleMatch ? titleMatch[1].trim() : path.basename(mdFile, '.md');

        const bodyHtml = injectHeadingIds(rewriteLinks(marked.parse(renderPlantuml(raw))));
        const outName = `${path.basename(mdFile, '.md')}.html`;
        fs.writeFileSync(path.join(OUT_DIR, outName), pageTemplate(title, bodyHtml, false), 'utf8');
        chapters.push({ file: outName, title });
      }

      // 読者向けの目次
      fs.writeFileSync(
        path.join(OUT_DIR, 'index.html'),
        pageTemplate(MANUAL_TITLE, tableOfContents(chapters), true),
        'utf8',
      );

      fs.writeFileSync(path.join(OUT_DIR, 'style.css'), STYLE_CSS, 'utf8');

      console.log(
        `マニュアルを HTML 変換しました: ${chapters.length} 章 + 目次 / 画像 ${assets} 点 → ${OUT_DIR}`,
      );
      done();
    } catch (error) {
      done(error);
    }
  });
}
