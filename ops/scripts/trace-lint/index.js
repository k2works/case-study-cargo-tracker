'use strict';

/**
 * ドキュメント整合の機械検査（trace-lint）
 *
 * カバレッジを計測しない本プロジェクトでは、トレーサビリティ表が網羅性の唯一の物差しになる
 * （docs/design/test_strategy.md 6.1）。**表が実態とずれることは、カバレッジ計測が
 * 壊れているのと同義**である。IT2 のレビューで実際にずれが 3 件見つかったため、
 * 人手の維持をやめて検査する（IT2 ふりかえり Try T6）。
 *
 * 検査するのは「表と実体の突合」であって、内容の正しさではない。
 */

import fs from 'fs';
import path from 'path';

// ============================================
// 設定
// ============================================

/** リポジトリのルート（本ファイルから 3 階層上） */
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../..');

/** ユーザーストーリーの正典 */
const USER_STORY = 'docs/requirements/user_story.md';

/** ストーリートレーサビリティ表 */
const TEST_STRATEGY = 'docs/design/test_strategy.md';

/** ビジネスルール ⇄ テスト対応表 */
const RULE_TRACEABILITY = 'docs/design/business_rule_traceability.md';

/** テストコードの置き場所 */
const TEST_ROOT = 'apps/cargo-tracker/test';

// ============================================
// ヘルパー関数
// ============================================

/**
 * ファイルを読む
 * @param {string} relative リポジトリルートからの相対パス
 * @returns {string} 内容
 */
function read(relative) {
  return fs.readFileSync(path.join(ROOT, relative), 'utf8');
}

/**
 * ユーザーストーリーの US 番号を抽出する
 * @returns {string[]} US 番号の配列
 */
function userStoryIds() {
  const source = read(USER_STORY);
  const ids = [...source.matchAll(/^## (US\d+):/gm)].map((m) => m[1]);
  return [...new Set(ids)];
}

/**
 * トレーサビリティ表に載っている US 番号を抽出する
 * @returns {string[]} US 番号の配列
 */
function tracedStoryIds() {
  const source = read(TEST_STRATEGY);
  const ids = [...source.matchAll(/^\| (US\d+) \|/gm)].map((m) => m[1]);
  return [...new Set(ids)];
}

/**
 * テストコードに実在する関数名を集める
 * @returns {Set<string>} `モジュール.関数` の集合
 */
function existingTestFunctions() {
  const found = new Set();
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
        continue;
      }
      if (!entry.name.endsWith('.flix')) continue;
      const source = fs.readFileSync(full, 'utf8');
      const moduleName = (source.match(/^mod\s+([A-Za-z0-9_]+)\s*\{/m) || [])[1];
      if (!moduleName) continue;
      for (const m of source.matchAll(/^\s*def\s+(test[A-Za-z0-9_]*)\s*\(/gm)) {
        found.add(`${moduleName}.${m[1]}`);
      }
    }
  };
  walk(path.join(ROOT, TEST_ROOT));
  return found;
}

/**
 * ビジネスルール対応表が「済」として参照しているテスト関数名を集める
 *
 * **状態が「済」の行のみを対象にする**。未着手・実装中の行に書かれたテスト名は
 * 「これから書く予定」を示すものであり、実在しないのが正常だからである。
 * 検査したいのは「済と書いてあるのにテストがない」という偽の緑である。
 *
 * `` `Module.testFoo` `` の形式のみを対象とする。日本語の説明（「（IT9）」等）は無視する。
 * @returns {{ rule: string, fn: string }[]} 参照の配列
 */
function referencedTestFunctions() {
  const source = read(RULE_TRACEABILITY);
  const refs = [];
  for (const line of source.split('\n')) {
    const ruleId = (line.match(/^\|\s*([A-Z]{2}-\d+)\s*\|/) || [])[1];
    if (!ruleId) continue;
    if (!/\|\s*\*\*済\*\*/.test(line)) continue;
    for (const m of line.matchAll(/`([A-Za-z0-9_]+\.test[A-Za-z0-9_]*)`/g)) {
      refs.push({ rule: ruleId, fn: m[1] });
    }
  }
  return refs;
}

// ============================================
// 規約
// ============================================

/**
 * 規約 1: すべての US がトレーサビリティ表に載っていること
 * @returns {string[]} 違反メッセージ
 */
function ruleAllStoriesTraced() {
  const defined = userStoryIds();
  const traced = new Set(tracedStoryIds());
  return defined
    .filter((id) => !traced.has(id))
    .map((id) => `${id} が ${TEST_STRATEGY} のトレーサビリティ表にありません`);
}

/**
 * 規約 2: 表に載っている US が実在すること（削除・番号変更の検出）
 * @returns {string[]} 違反メッセージ
 */
function ruleNoOrphanStories() {
  const defined = new Set(userStoryIds());
  return tracedStoryIds()
    .filter((id) => !defined.has(id))
    .map((id) => `${id} は ${TEST_STRATEGY} にありますが ${USER_STORY} に存在しません`);
}

/**
 * 規約 3: ビジネスルール対応表が参照するテスト関数が実在すること
 *
 * 「済」と書かれているのにテストが存在しない状態は、代替統制が偽の緑を出している。
 * @returns {string[]} 違反メッセージ
 */
function ruleReferencedTestsExist() {
  const existing = existingTestFunctions();
  return referencedTestFunctions()
    .filter(({ fn }) => !existing.has(fn))
    .map(({ rule, fn }) =>
      `${rule} が参照するテスト ${fn} が ${TEST_ROOT} に存在しません`);
}

/**
 * 規約 0: 検査器自身が機能していること
 *
 * 正規表現の書き間違いで 1 件も抽出しなくなると、**壊れたまま「違反 0 件」で緑になる**。
 * カバレッジの代わりに信頼している検査が静かに死ぬのが最も危険なので、
 * 抽出結果が空であること自体を違反として扱う（arch-lint のメタテストに相当する最小の自衛）。
 * @returns {string[]} 違反メッセージ
 */
function ruleExtractorsWork() {
  const violations = [];
  if (userStoryIds().length === 0) {
    violations.push(`${USER_STORY} から US 番号を 1 件も抽出できません（検査器が壊れている可能性）`);
  }
  if (tracedStoryIds().length === 0) {
    violations.push(`${TEST_STRATEGY} から US 番号を 1 件も抽出できません（検査器が壊れている可能性）`);
  }
  if (existingTestFunctions().size === 0) {
    violations.push(`${TEST_ROOT} からテスト関数を 1 件も抽出できません（検査器が壊れている可能性）`);
  }
  if (referencedTestFunctions().length === 0) {
    violations.push(`${RULE_TRACEABILITY} から「済」のテスト参照を 1 件も抽出できません（検査器が壊れている可能性）`);
  }
  return violations;
}

/** 全規約 */
const RULES = [ruleExtractorsWork, ruleAllStoriesTraced, ruleNoOrphanStories, ruleReferencedTestsExist];

// ============================================
// 実行
// ============================================

/**
 * ドキュメント整合を検査する
 * @returns {string[]} 違反メッセージの配列
 */
export function lintTraceability() {
  return RULES.flatMap((rule) => rule());
}
