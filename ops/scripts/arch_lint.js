'use strict';

/**
 * アーキテクチャ規約検査（arch-lint）の Gulp タスク
 *
 * ArchUnit が使えない Flix において、レイヤ依存・コンテキスト独立性などの
 * 規約を機械的に検査する（ADR-0002）。規約の正典は docs/design/arch_lint_rules.md。
 */

import { lintProject } from './arch-lint/index.js';
import { runMetaTest } from './arch-lint/meta-test.js';

// ============================================
// ヘルパー関数
// ============================================

/**
 * 違反を人が読める形式で出力する
 * @param {object[]} violations 違反の配列
 * @returns {void}
 */
function reportViolations(violations) {
  if (violations.length === 0) {
    console.log('アーキテクチャ規約違反: 0 件');
    return;
  }
  console.error(`\nアーキテクチャ規約違反: ${violations.length} 件\n`);
  const byRule = violations.reduce((acc, v) => {
    (acc[v.ruleId] = acc[v.ruleId] || []).push(v);
    return acc;
  }, {});
  for (const [ruleId, list] of Object.entries(byRule).sort()) {
    console.error(`  ${ruleId}（${list.length} 件）`);
    for (const v of list) {
      console.error(`    ${v.file}:${v.line}`);
      console.error(`      ${v.message}`);
    }
    console.error('');
  }
  console.error('規約の詳細は docs/design/arch_lint_rules.md を参照してください。\n');
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  gulp.task('arch:lint', (done) => {
    const violations = lintProject();
    reportViolations(violations);
    if (violations.length > 0) {
      process.exitCode = 1;
      done(new Error(`アーキテクチャ規約違反が ${violations.length} 件あります`));
      return;
    }
    done();
  });

  gulp.task('arch:lint:test', (done) => {
    const result = runMetaTest();
    console.log(`\narch-lint メタテスト: ${result.passed} 件成功 / ${result.failed} 件失敗\n`);
    if (result.failed > 0) {
      for (const f of result.failures) console.error(`  ${f}`);
      console.error('');
      process.exitCode = 1;
      done(new Error('arch-lint のメタテストが失敗しました。検査結果を信用できません'));
      return;
    }
    done();
  });

  /** 検査本体とメタテストをまとめて実行する */
  gulp.task('arch:check', gulp.series('arch:lint:test', 'arch:lint'));

  gulp.task('arch:help', (done) => {
    console.log(`
=== アーキテクチャ規約検査 ===

  arch:lint        プロジェクトを検査する（違反があれば終了コード 1）
  arch:lint:test   メタテスト（フィクスチャによる自己検証）
  arch:check       メタテスト → 検査を順に実行する
  arch:help        このヘルプを表示する

  規約の正典: docs/design/arch_lint_rules.md
`);
    done();
  });
}
