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
import appTasks from './ops/scripts/app.js';
import manualTasks from './ops/scripts/manual.js';
import deployTasks from './ops/scripts/deploy.js';
import releaseTasks from './ops/scripts/release.js';

// Load gulp tasks from script modules
mkdocsTasks(gulp);
journalTasks(gulp);
vaultTasks(gulp);
sshTasks(gulp);
sonarLocalTasks(gulp);
appTasks(gulp);
manualTasks(gulp);
// deploy は他モジュールのタスク（mkdocs / app / manual）を series で参照するため最後に登録する
deployTasks(gulp);
// **タグは実装ごとに分ける。** 素の `v1.1.0` では、同一リポジトリに同居する
// 他の実装（言語 / take）のリリースと判別できない
releaseTasks(gulp, { tagPrefix: 'java/take-6/' });

export const spec = gulp.series('mkdocs:serve', 'mkdocs:open');

// Export gulp to make it available to the gulp CLI
export default gulp;
