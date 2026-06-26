'use strict';

/**
 * Gulpfile that loads tasks from the script directory
 */

import 'dotenv/config';
import gulp from 'gulp';
import mkdocsTasks from './ops/scripts/mkdocs.js';
import journalTasks from './ops/scripts/journal.js';
import vaultTasks from './ops/scripts/vault.js';
import sshTasks from './ops/scripts/ssh.js';
import sonarLocalTasks from './ops/scripts/sonar_local.js';
import developTasks from './ops/scripts/develop.js';

// Load gulp tasks from script modules
mkdocsTasks(gulp);
journalTasks(gulp);
vaultTasks(gulp);
sshTasks(gulp);
sonarLocalTasks(gulp);
developTasks(gulp);

export const spec = gulp.series('mkdocs:serve', 'mkdocs:open');

// アプリ + ドキュメントサーバー同時起動
// mkdocs:serve は docker compose -d で即時 return するため、その後 dev:run が foreground 起動する
export const start = gulp.series(
  'dev:db:start',
  'mkdocs:serve',
  'mkdocs:open',
  gulp.parallel('dev:run', 'dev:open'),
);

// 停止用 (Ctrl+C で dev:run を止めた後、ドキュメント/DB コンテナを止める)
export const stop = gulp.series('mkdocs:stop', 'dev:db:stop');

// Export gulp to make it available to the gulp CLI
export default gulp;
