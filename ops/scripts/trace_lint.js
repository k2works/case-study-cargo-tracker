'use strict';

/**
 * ドキュメント整合検査（trace-lint）の Gulp タスク
 *
 * カバレッジを計測しない本プロジェクトでは、トレーサビリティ表が網羅性の唯一の物差しになる。
 * 表が実態とずれることは、カバレッジ計測が壊れているのと同義であるため CI で検査する。
 */

import { lintTraceability } from './trace-lint/index.js';

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  gulp.task('trace:lint', (done) => {
    const violations = lintTraceability();

    if (violations.length === 0) {
      console.log('\nドキュメント整合違反: 0 件\n');
      done();
      return;
    }

    console.error(`\nドキュメント整合違反: ${violations.length} 件\n`);
    violations.forEach((message) => console.error(`  ${message}`));
    console.error('\n正典は docs/requirements/user_story.md と docs/design/business_rule_traceability.md です。\n');
    done(new Error(`ドキュメント整合違反が ${violations.length} 件あります`));
  });

  gulp.task('trace:help', (done) => {
    console.log(`
=== ドキュメント整合検査 ===

  trace:lint       ユーザーストーリー・トレーサビリティ表・テストの突合
  trace:help       このヘルプを表示する

検査内容:
  1. すべての US がストーリートレーサビリティ表に載っていること
  2. 表に載っている US が実在すること（削除・番号変更の検出）
  3. ビジネスルール対応表が「済」として挙げるテストが実在すること
`);
    done();
  });
}
