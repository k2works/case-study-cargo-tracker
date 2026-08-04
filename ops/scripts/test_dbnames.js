/**
 * テスト用 DB 名の重複検査タスク（IT9）。
 *
 * `arch:check` と同じく、**テストを走らせる前に**落ちる種類の問題を潰す。
 */

import { reportDuplicates } from './test-dbnames/index.js';

export default function (gulp) {
  gulp.task('test:dbnames', (done) => {
    const duplicates = reportDuplicates();
    if (duplicates > 0) {
      process.exitCode = 1;
      done(new Error(`テスト用 DB 名の重複が ${duplicates} 件あります`));
      return;
    }
    done();
  });
}
