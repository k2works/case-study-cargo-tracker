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

// ドキュメントサーバー起動（旧 gulp dev。アプリ開発サーバーは gulp dev を使用）
export const docs = gulp.series('mkdocs:serve', 'mkdocs:open');

// npm run start: ドキュメントサーバー（mkdocs）とアプリ開発サーバーを一括起動
// アプリは起動完了（/health 応答）後にブラウザで自動オープンする
gulp.task('start', gulp.series('dev:db:start', 'mkdocs:serve', 'mkdocs:open', 'dev:app:open'));

// Export gulp to make it available to the gulp CLI
export default gulp;
