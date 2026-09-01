'use strict';

/**
 * Gulpfile that loads tasks from the script directory
 */

import 'dotenv/config';
import gulp from 'gulp';
import mkdocsTasks from './ops/scripts/mkdocs.js';
import manualTasks from './ops/scripts/manual.js';
import journalTasks from './ops/scripts/journal.js';
import vaultTasks from './ops/scripts/vault.js';
import sshTasks from './ops/scripts/ssh.js';
import sonarLocalTasks from './ops/scripts/sonar_local.js';
import developTasks from './ops/scripts/develop.js';
import deployTasks from './ops/scripts/deploy.js';
import releaseTasks from './ops/scripts/release.js';

// Load gulp tasks from script modules
mkdocsTasks(gulp);
manualTasks(gulp);
journalTasks(gulp);
vaultTasks(gulp);
sshTasks(gulp);
sonarLocalTasks(gulp);
developTasks(gulp);
deployTasks(gulp);
// **読み込みを忘れるとタスクが存在しない。** release.js は書かれていたのに
// gulpfile へ登録されておらず、5 リリース連続でタグと CHANGELOG が作られなかった
releaseTasks(gulp);

export const spec = gulp.series('mkdocs:serve', 'mkdocs:open');

// Export gulp to make it available to the gulp CLI
export default gulp;
